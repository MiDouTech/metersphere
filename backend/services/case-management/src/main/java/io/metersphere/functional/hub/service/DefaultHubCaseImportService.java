package io.metersphere.functional.hub.service;

import io.metersphere.functional.constants.FunctionalCaseReviewStatus;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseBlob;
import io.metersphere.functional.domain.FunctionalCaseExample;
import io.metersphere.functional.hub.dao.DefaultHubSyncJobDao;
import io.metersphere.functional.hub.dto.DefaultHubCaseImportRequest;
import io.metersphere.functional.hub.dto.DefaultHubJobResponse;
import io.metersphere.functional.hub.dto.DefaultHubSyncJobRow;
import io.metersphere.functional.mapper.FunctionalCaseBlobMapper;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.functional.service.FunctionalCaseCustomFieldService;
import io.metersphere.functional.service.FunctionalCaseModuleService;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.project.mapper.ExtBaseProjectVersionMapper;
import io.metersphere.sdk.constants.ApplicationNumScope;
import io.metersphere.sdk.constants.DefaultHubConstants;
import io.metersphere.sdk.constants.ExecStatus;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.dto.sdk.BaseTreeNode;
import io.metersphere.system.service.DefaultHubProjectService;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.uid.NumGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从默认项目导入用例到业务项目（不写 hub map）
 */
@Service
public class DefaultHubCaseImportService {

    @Resource
    private DefaultHubProjectService defaultHubProjectService;
    @Resource
    private DefaultHubModuleResolver defaultHubModuleResolver;
    @Resource
    private DefaultHubSyncJobDao defaultHubSyncJobDao;
    @Lazy
    @Resource
    private FunctionalCaseModuleService functionalCaseModuleService;
    @Resource
    private FunctionalCaseMapper functionalCaseMapper;
    @Resource
    private FunctionalCaseBlobMapper functionalCaseBlobMapper;
    @Resource
    private FunctionalCaseCustomFieldService functionalCaseCustomFieldService;
    @Lazy
    @Resource
    private FunctionalCaseService functionalCaseService;
    @Resource
    private ExtBaseProjectVersionMapper extBaseProjectVersionMapper;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Lazy
    @Resource
    private DefaultHubCaseImportService self;

    public DefaultHubJobResponse startImport(DefaultHubCaseImportRequest request, String operator) {
        if (defaultHubProjectService.isDefaultProject(request.getTargetProjectId())) {
            throw new MSException("不能导入到默认枢纽项目");
        }
        String hubProjectId = defaultHubProjectService.getDefaultProjectId();
        if (StringUtils.isBlank(hubProjectId)) {
            throw new MSException("default hub project not configured");
        }
        assertSelectModeAllowed(request.getSelectMode());
        List<String> sourceCaseIds = resolveSourceCaseIds(hubProjectId, request);
        sourceCaseIds = filterPlannedHubCases(hubProjectId, sourceCaseIds);
        if (sourceCaseIds.size() > DefaultHubConstants.MAX_CASE_IMPORT_BATCH) {
            throw new MSException("单次导入用例不能超过 " + DefaultHubConstants.MAX_CASE_IMPORT_BATCH + " 条");
        }
        if (CollectionUtils.isEmpty(sourceCaseIds)) {
            throw new MSException("未选择可导入的用例");
        }
        String jobId = defaultHubSyncJobDao.createJob(DefaultHubConstants.JOB_TYPE_IMPORT_CASE,
                request.getTargetProjectId(), operator);
        // 经代理调用以保证 @Async 生效
        self.executeImportAsync(jobId, hubProjectId, request, sourceCaseIds, operator);
        DefaultHubJobResponse resp = new DefaultHubJobResponse();
        resp.setJobId(jobId);
        resp.setStatus(DefaultHubConstants.JOB_STATUS_PENDING);
        resp.setProgress(0);
        return resp;
    }

    /**
     * 默认项目导入勾选树：模块/文件夹 + 已归类用例叶子（排除未规划 root）。
     */
    public List<BaseTreeNode> getImportTree() {
        String hubProjectId = defaultHubProjectService.getDefaultProjectId();
        if (StringUtils.isBlank(hubProjectId)) {
            throw new MSException("default hub project not configured");
        }
        List<BaseTreeNode> tree = functionalCaseModuleService.getTreeWithoutVirtualRoot(hubProjectId);
        Map<String, List<BaseTreeNode>> casesByModule = loadPlannedCaseLeaves(hubProjectId);
        attachCaseLeaves(tree, casesByModule);
        return tree;
    }

    public DefaultHubJobResponse getJob(String jobId) {
        DefaultHubSyncJobRow row = defaultHubSyncJobDao.findById(jobId);
        if (row == null) {
            throw new MSException("任务不存在");
        }
        DefaultHubJobResponse resp = new DefaultHubJobResponse();
        resp.setJobId(row.getId());
        resp.setStatus(row.getStatus());
        resp.setProgress(row.getProgress());
        resp.setSuccessCount(row.getSuccessCount());
        resp.setFailCount(row.getFailCount());
        resp.setErrorMessage(row.getErrorMessage());
        return resp;
    }

    @Async
    public void executeImportAsync(String jobId, String hubProjectId, DefaultHubCaseImportRequest request,
                                   List<String> sourceCaseIds, String operator) {
        if (!defaultHubSyncJobDao.tryAcquire(jobId)) {
            return;
        }
        int total = sourceCaseIds.size();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                int success = 0;
                int index = 0;
                for (String hubCaseId : sourceCaseIds) {
                    importOne(hubProjectId, hubCaseId, request, operator);
                    success++;
                    index++;
                    int progress = (int) (index * 100L / total);
                    defaultHubSyncJobDao.updateProgress(jobId, progress, success, 0);
                }
                defaultHubSyncJobDao.finish(jobId, DefaultHubConstants.JOB_STATUS_SUCCESS, 100, success, 0, null);
            });
        } catch (Exception e) {
            LogUtils.error("default hub case import failed, job=" + jobId, e);
            defaultHubSyncJobDao.finish(jobId, DefaultHubConstants.JOB_STATUS_FAILED, 0, 0, total,
                    StringUtils.defaultString(e.getMessage()));
            throw e;
        }
    }

    private void importOne(String hubProjectId, String hubCaseId, DefaultHubCaseImportRequest request, String operator) {
        FunctionalCase hubCase = functionalCaseMapper.selectByPrimaryKey(hubCaseId);
        if (hubCase == null || Boolean.TRUE.equals(hubCase.getDeleted())) {
            return;
        }
        String targetModuleId = defaultHubModuleResolver.resolveTargetModuleId(
                hubProjectId, hubCase.getModuleId(), request.getTargetProjectId(), operator);
        FunctionalCaseExample example = new FunctionalCaseExample();
        example.createCriteria().andProjectIdEqualTo(request.getTargetProjectId())
                .andModuleIdEqualTo(targetModuleId).andNameEqualTo(hubCase.getName()).andDeletedEqualTo(false);
        List<FunctionalCase> exists = functionalCaseMapper.selectByExample(example);
        if (CollectionUtils.isNotEmpty(exists)) {
            if (DefaultHubConstants.CONFLICT_SKIP.equals(request.getConflictStrategy())) {
                return;
            }
            overwriteCase(exists.getFirst(), hubCaseId, operator);
            return;
        }
        createImportedCase(hubCase, hubCaseId, request.getTargetProjectId(), targetModuleId, operator);
    }

    private void createImportedCase(FunctionalCase hubCase, String hubCaseId, String targetProjectId,
                                    String targetModuleId, String operator) {
        String newId = IDGenerator.nextStr();
        FunctionalCase target = new FunctionalCase();
        target.setId(newId);
        target.setProjectId(targetProjectId);
        target.setModuleId(targetModuleId);
        target.setTemplateId(hubCase.getTemplateId());
        target.setName(hubCase.getName());
        target.setTags(hubCase.getTags());
        target.setCaseEditType(hubCase.getCaseEditType());
        target.setNum(NumGenerator.nextNum(targetProjectId, ApplicationNumScope.CASE_MANAGEMENT));
        target.setPos(functionalCaseService.getNextOrder(targetProjectId));
        target.setVersionId(StringUtils.defaultIfBlank(hubCase.getVersionId(),
                extBaseProjectVersionMapper.getDefaultVersion(targetProjectId)));
        target.setRefId(newId);
        target.setReviewStatus(FunctionalCaseReviewStatus.UN_REVIEWED.name());
        target.setLastExecuteResult(ExecStatus.PENDING.name());
        target.setDeleted(false);
        target.setAiCreate(false);
        target.setPublicCase(false);
        target.setLatest(true);
        target.setCreateUser(operator);
        target.setUpdateUser(operator);
        long now = System.currentTimeMillis();
        target.setCreateTime(now);
        target.setUpdateTime(now);
        functionalCaseMapper.insertSelective(target);
        jdbcTemplate.update("UPDATE functional_case SET imported_from_hub_case_id = ? WHERE id = ?", hubCaseId, newId);
        copyBlob(hubCaseId, newId, true);
        functionalCaseCustomFieldService.copyCustomField(hubCaseId, newId);
    }

    private void overwriteCase(FunctionalCase target, String hubCaseId, String operator) {
        FunctionalCase hubCase = functionalCaseMapper.selectByPrimaryKey(hubCaseId);
        FunctionalCase update = new FunctionalCase();
        update.setId(target.getId());
        update.setName(hubCase.getName());
        update.setTags(hubCase.getTags());
        update.setCaseEditType(hubCase.getCaseEditType());
        update.setReviewStatus(FunctionalCaseReviewStatus.UN_REVIEWED.name());
        update.setLastExecuteResult(ExecStatus.PENDING.name());
        update.setExecuteUser(null);
        update.setUpdateUser(operator);
        update.setUpdateTime(System.currentTimeMillis());
        functionalCaseMapper.updateByPrimaryKeySelective(update);
        copyBlob(hubCaseId, target.getId(), false);
        functionalCaseCustomFieldService.copyCustomField(hubCaseId, target.getId());
        jdbcTemplate.update("UPDATE functional_case SET imported_from_hub_case_id = ? WHERE id = ?", hubCaseId, target.getId());
    }

    private void copyBlob(String fromId, String toId, boolean insertIfMissing) {
        FunctionalCaseBlob src = functionalCaseBlobMapper.selectByPrimaryKey(fromId);
        FunctionalCaseBlob blob = new FunctionalCaseBlob();
        blob.setId(toId);
        if (src != null) {
            blob.setSteps(src.getSteps());
            blob.setTextDescription(src.getTextDescription());
            blob.setExpectedResult(src.getExpectedResult());
            blob.setPrerequisite(src.getPrerequisite());
            blob.setDescription(src.getDescription());
        }
        if (insertIfMissing || functionalCaseBlobMapper.selectByPrimaryKey(toId) == null) {
            functionalCaseBlobMapper.insertSelective(blob);
        } else {
            functionalCaseBlobMapper.updateByPrimaryKeyWithBLOBs(blob);
        }
    }

    private List<String> resolveSourceCaseIds(String hubProjectId, DefaultHubCaseImportRequest request) {
        Set<String> caseIds = new HashSet<>();
        String mode = request.getSelectMode();
        if (DefaultHubConstants.SELECT_CASE_IDS.equals(mode)) {
            caseIds.addAll(nullToEmpty(request.getIds()));
        } else if (DefaultHubConstants.SELECT_MODULE_IDS.equals(mode)
                || DefaultHubConstants.SELECT_FOLDER_IDS.equals(mode)) {
            List<String> moduleIds = defaultHubModuleResolver.listDescendantModuleIds(hubProjectId, request.getIds());
            caseIds.addAll(listCaseIdsByProject(hubProjectId, moduleIds));
        } else {
            throw new MSException("不支持的选择模式: " + mode);
        }
        return new ArrayList<>(caseIds);
    }

    private void assertSelectModeAllowed(String mode) {
        if (DefaultHubConstants.SELECT_ALL.equals(mode) || DefaultHubConstants.SELECT_UNPLANNED.equals(mode)) {
            throw new MSException("不支持全部/未规划导入，请勾选已归类模块或用例");
        }
        if (!StringUtils.equalsAny(mode,
                DefaultHubConstants.SELECT_CASE_IDS,
                DefaultHubConstants.SELECT_MODULE_IDS,
                DefaultHubConstants.SELECT_FOLDER_IDS)) {
            throw new MSException("不支持的选择模式: " + mode);
        }
    }

    /**
     * 仅保留枢纽项目内、且不属于未规划（module_id=root）的用例。
     */
    private List<String> filterPlannedHubCases(String hubProjectId, List<String> caseIds) {
        if (CollectionUtils.isEmpty(caseIds)) {
            return List.of();
        }
        String placeholders = String.join(",", caseIds.stream().map(id -> "?").toList());
        Object[] args = new Object[caseIds.size() + 2];
        args[0] = hubProjectId;
        args[1] = ModuleConstants.DEFAULT_NODE_ID;
        for (int i = 0; i < caseIds.size(); i++) {
            args[i + 2] = caseIds.get(i);
        }
        return jdbcTemplate.queryForList(
                "SELECT id FROM functional_case WHERE project_id = ? AND deleted = 0 AND module_id <> ? AND id IN ("
                        + placeholders + ")",
                String.class, args);
    }

    private Map<String, List<BaseTreeNode>> loadPlannedCaseLeaves(String hubProjectId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, num, name, module_id FROM functional_case " +
                        "WHERE project_id = ? AND deleted = 0 AND module_id IS NOT NULL AND module_id <> ? " +
                        "ORDER BY num ASC",
                hubProjectId, ModuleConstants.DEFAULT_NODE_ID);
        Map<String, List<BaseTreeNode>> byModule = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String moduleId = String.valueOf(row.get("module_id"));
            String caseId = String.valueOf(row.get("id"));
            Object numObj = row.get("num");
            String num = numObj == null ? "" : String.valueOf(numObj);
            String name = row.get("name") == null ? "" : String.valueOf(row.get("name"));
            BaseTreeNode leaf = new BaseTreeNode(caseId, num + " " + name, "CASE", moduleId);
            leaf.putAttachInfo("num", num);
            leaf.putAttachInfo("caseName", name);
            byModule.computeIfAbsent(moduleId, k -> new ArrayList<>()).add(leaf);
        }
        return byModule;
    }

    private void attachCaseLeaves(List<BaseTreeNode> nodes, Map<String, List<BaseTreeNode>> casesByModule) {
        if (CollectionUtils.isEmpty(nodes)) {
            return;
        }
        for (BaseTreeNode node : nodes) {
            if (node.getChildren() == null) {
                node.setChildren(new ArrayList<>());
            }
            attachCaseLeaves(node.getChildren(), casesByModule);
            List<BaseTreeNode> cases = casesByModule.get(node.getId());
            if (CollectionUtils.isNotEmpty(cases)) {
                node.getChildren().addAll(cases);
            }
        }
    }

    private List<String> listCaseIdsByProject(String projectId, List<String> moduleIds) {
        if (moduleIds == null) {
            return jdbcTemplate.queryForList(
                    "SELECT id FROM functional_case WHERE project_id = ? AND deleted = 0 AND module_id <> ?",
                    String.class, projectId, ModuleConstants.DEFAULT_NODE_ID);
        }
        if (moduleIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", moduleIds.stream().map(id -> "?").toList());
        Object[] args = new Object[moduleIds.size() + 2];
        args[0] = projectId;
        args[1] = ModuleConstants.DEFAULT_NODE_ID;
        for (int i = 0; i < moduleIds.size(); i++) {
            args[i + 2] = moduleIds.get(i);
        }
        return jdbcTemplate.queryForList(
                "SELECT id FROM functional_case WHERE project_id = ? AND deleted = 0 AND module_id <> ? AND module_id IN ("
                        + placeholders + ")",
                String.class, args);
    }

    private List<String> nullToEmpty(List<String> ids) {
        return ids == null ? List.of() : ids;
    }
}
