package io.metersphere.functional.hub.service;

import io.metersphere.functional.constants.FunctionalCaseReviewStatus;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseBlob;
import io.metersphere.functional.hub.dao.DefaultHubCaseMapDao;
import io.metersphere.functional.hub.dto.DefaultHubCaseMapRow;
import io.metersphere.functional.mapper.FunctionalCaseBlobMapper;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.functional.service.FunctionalCaseCustomFieldService;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.project.mapper.ExtBaseProjectVersionMapper;
import io.metersphere.sdk.constants.ApplicationNumScope;
import io.metersphere.sdk.constants.ExecStatus;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.service.DefaultHubProjectService;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.uid.NumGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * 业务用例 → 默认项目枢纽镜像同步
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class DefaultHubCaseSyncService {

    @Resource
    private DefaultHubConfigService defaultHubConfigService;
    @Resource
    private DefaultHubProjectService defaultHubProjectService;
    @Resource
    private DefaultHubCaseMapDao defaultHubCaseMapDao;
    @Resource
    private DefaultHubModuleResolver defaultHubModuleResolver;
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
    private JdbcTemplate jdbcTemplate;

    /**
     * 业务用例 upsert 到枢纽；导入副本（imported_from_hub）跳过
     */
    public void syncCaseUpsert(String bizProjectId, String bizCaseId, String operator) {
        if (!defaultHubConfigService.isSyncEnabled()) {
            return;
        }
        if (defaultHubProjectService.isDefaultProject(bizProjectId)) {
            return;
        }
        if (isImportedFromHub(bizCaseId)) {
            return;
        }
        FunctionalCase bizCase = functionalCaseMapper.selectByPrimaryKey(bizCaseId);
        if (bizCase == null || Boolean.TRUE.equals(bizCase.getDeleted())) {
            return;
        }
        DefaultHubCaseMapRow map = defaultHubCaseMapDao.findByBizCaseId(bizCaseId);
        // 防环：更新路径仅处理已建立映射的用例；新建时 map 为空仍允许首次同步
        if (map != null && isImportedFromHub(bizCaseId)) {
            return;
        }

        String hubProjectId = defaultHubProjectService.getDefaultProjectId();
        if (StringUtils.isBlank(hubProjectId)) {
            LogUtils.warn("default hub sync skipped, no default project");
            return;
        }
        String hubModuleId = defaultHubModuleResolver.resolveHubModuleId(bizProjectId, bizCase.getModuleId(), hubProjectId, operator);
        if (StringUtils.isBlank(hubModuleId)) {
            LogUtils.warn("default hub sync skipped, no hub folder for project=" + bizProjectId);
            return;
        }

        FunctionalCaseBlob blob = functionalCaseBlobMapper.selectByPrimaryKey(bizCaseId);
        String contentHash = computeHash(bizCase, blob);

        if (map == null) {
            String hubCaseId = createHubMirror(bizCase, blob, hubProjectId, hubModuleId, operator);
            defaultHubCaseMapDao.insert(bizProjectId, bizCaseId, hubCaseId, contentHash);
        } else if (!StringUtils.equals(contentHash, map.getContentHash())) {
            updateHubMirror(map.getHubCaseId(), bizCase, blob, hubModuleId, operator);
            defaultHubCaseMapDao.updateHash(bizCaseId, contentHash);
        }
    }

    /** 业务用例删除 → 枢纽镜像进回收站并删 map */
    public void syncCaseDelete(String bizCaseId, String operator) {
        if (!defaultHubConfigService.isSyncEnabled()) {
            return;
        }
        DefaultHubCaseMapRow map = defaultHubCaseMapDao.findByBizCaseId(bizCaseId);
        if (map == null) {
            return;
        }
        FunctionalCase hubCase = new FunctionalCase();
        hubCase.setId(map.getHubCaseId());
        hubCase.setDeleted(true);
        hubCase.setDeleteUser(operator);
        hubCase.setDeleteTime(System.currentTimeMillis());
        functionalCaseMapper.updateByPrimaryKeySelective(hubCase);
        defaultHubCaseMapDao.deleteByBizCaseId(bizCaseId);
    }

    public void reconcileProjectCases(String bizProjectId, String operator, ProgressCallback callback) {
        if (defaultHubProjectService.isDefaultProject(bizProjectId)) {
            return;
        }
        List<String> allBizCaseIds = jdbcTemplate.queryForList(
                "SELECT id FROM functional_case WHERE project_id = ? AND deleted = 0 " +
                        "AND (imported_from_hub_case_id IS NULL OR imported_from_hub_case_id = '')",
                String.class, bizProjectId);
        int total = allBizCaseIds.size();
        int done = 0;
        int success = 0;
        for (String caseId : allBizCaseIds) {
            try {
                syncCaseUpsert(bizProjectId, caseId, operator);
                success++;
            } catch (Exception e) {
                LogUtils.error("hub case reconcile failed: " + caseId, e);
            }
            done++;
            if (callback != null) {
                callback.onProgress(done, total, success, done - success);
            }
        }
        // 清理孤儿映射
        for (DefaultHubCaseMapRow map : defaultHubCaseMapDao.listByBizProjectId(bizProjectId)) {
            FunctionalCase biz = functionalCaseMapper.selectByPrimaryKey(map.getBizCaseId());
            if (biz == null || Boolean.TRUE.equals(biz.getDeleted())) {
                syncCaseDelete(map.getBizCaseId(), operator);
            }
        }
    }

    private boolean isImportedFromHub(String caseId) {
        try {
            String imported = jdbcTemplate.queryForObject(
                    "SELECT imported_from_hub_case_id FROM functional_case WHERE id = ?", String.class, caseId);
            return StringUtils.isNotBlank(imported);
        } catch (Exception e) {
            return false;
        }
    }

    private String createHubMirror(FunctionalCase src, FunctionalCaseBlob blob, String hubProjectId, String hubModuleId, String operator) {
        String hubCaseId = IDGenerator.nextStr();
        FunctionalCase hub = copyCaseHeader(src, hubCaseId, hubProjectId, hubModuleId, operator);
        hub.setNum(NumGenerator.nextNum(hubProjectId, ApplicationNumScope.CASE_MANAGEMENT));
        hub.setPos(functionalCaseService.getNextOrder(hubProjectId));
        functionalCaseMapper.insertSelective(hub);
        insertBlob(hubCaseId, blob);
        functionalCaseCustomFieldService.copyCustomField(src.getId(), hubCaseId);
        return hubCaseId;
    }

    private void updateHubMirror(String hubCaseId, FunctionalCase src, FunctionalCaseBlob blob, String hubModuleId, String operator) {
        String hubProjectId = defaultHubProjectService.getDefaultProjectId();
        FunctionalCase hub = copyCaseHeader(src, hubCaseId, hubProjectId, hubModuleId, operator);
        hub.setReviewStatus(FunctionalCaseReviewStatus.UN_REVIEWED.name());
        hub.setLastExecuteResult(ExecStatus.PENDING.name());
        hub.setExecuteUser(null);
        functionalCaseMapper.updateByPrimaryKeySelective(hub);
        FunctionalCaseBlob hubBlob = new FunctionalCaseBlob();
        hubBlob.setId(hubCaseId);
        if (blob != null) {
            hubBlob.setSteps(blob.getSteps());
            hubBlob.setTextDescription(blob.getTextDescription());
            hubBlob.setExpectedResult(blob.getExpectedResult());
            hubBlob.setPrerequisite(blob.getPrerequisite());
            hubBlob.setDescription(blob.getDescription());
        }
        functionalCaseBlobMapper.updateByPrimaryKeyWithBLOBs(hubBlob);
        functionalCaseCustomFieldService.copyCustomField(src.getId(), hubCaseId);
    }

    private FunctionalCase copyCaseHeader(FunctionalCase src, String id, String projectId, String moduleId, String operator) {
        FunctionalCase c = new FunctionalCase();
        c.setId(id);
        c.setProjectId(projectId);
        c.setModuleId(moduleId);
        c.setTemplateId(src.getTemplateId());
        c.setName(src.getName());
        c.setTags(src.getTags());
        c.setCaseEditType(src.getCaseEditType());
        c.setVersionId(StringUtils.defaultIfBlank(src.getVersionId(),
                extBaseProjectVersionMapper.getDefaultVersion(projectId)));
        c.setRefId(id);
        c.setReviewStatus(FunctionalCaseReviewStatus.UN_REVIEWED.name());
        c.setLastExecuteResult(ExecStatus.PENDING.name());
        c.setDeleted(false);
        c.setAiCreate(false);
        c.setPublicCase(false);
        c.setLatest(true);
        c.setCreateUser(operator);
        c.setUpdateUser(operator);
        long now = System.currentTimeMillis();
        c.setCreateTime(now);
        c.setUpdateTime(now);
        return c;
    }

    private void insertBlob(String caseId, FunctionalCaseBlob src) {
        FunctionalCaseBlob blob = new FunctionalCaseBlob();
        blob.setId(caseId);
        if (src != null) {
            blob.setSteps(src.getSteps());
            blob.setTextDescription(src.getTextDescription());
            blob.setExpectedResult(src.getExpectedResult());
            blob.setPrerequisite(src.getPrerequisite());
            blob.setDescription(src.getDescription());
        } else {
            blob.setSteps(new byte[0]);
            blob.setTextDescription(new byte[0]);
            blob.setExpectedResult(new byte[0]);
            blob.setPrerequisite(new byte[0]);
            blob.setDescription(new byte[0]);
        }
        functionalCaseBlobMapper.insertSelective(blob);
    }

    String computeHash(FunctionalCase c, FunctionalCaseBlob blob) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.getName()).append('|').append(c.getModuleId()).append('|').append(c.getCaseEditType());
        if (blob != null) {
            sb.append('|').append(bytes(blob.getSteps())).append(bytes(blob.getTextDescription()))
                    .append(bytes(blob.getExpectedResult())).append(bytes(blob.getPrerequisite()))
                    .append(bytes(blob.getDescription()));
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return sb.toString();
        }
    }

    private String bytes(byte[] data) {
        return data == null ? "" : new String(data, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int done, int total, int success, int fail);
    }
}
