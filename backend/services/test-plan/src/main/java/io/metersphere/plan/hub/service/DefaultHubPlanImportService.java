package io.metersphere.plan.hub.service;

import io.metersphere.functional.hub.dao.DefaultHubSyncJobDao;
import io.metersphere.functional.hub.dto.DefaultHubJobResponse;
import io.metersphere.functional.hub.dto.DefaultHubSyncJobRow;
import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.domain.TestPlanConfig;
import io.metersphere.plan.domain.TestPlanDocument;
import io.metersphere.plan.domain.TestPlanExample;
import io.metersphere.plan.hub.dto.DefaultHubPlanImportRequest;
import io.metersphere.plan.mapper.TestPlanConfigMapper;
import io.metersphere.plan.mapper.TestPlanDocumentMapper;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.plan.service.TestPlanService;
import io.metersphere.sdk.constants.ApplicationNumScope;
import io.metersphere.sdk.constants.DefaultHubConstants;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.constants.TestPlanConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.LogUtils;
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

import java.util.List;

/**
 * 从默认项目单条导入测试计划（不写 hub map）
 */
@Service
public class DefaultHubPlanImportService {

    @Resource
    private DefaultHubProjectService defaultHubProjectService;
    @Resource
    private DefaultHubSyncJobDao defaultHubSyncJobDao;
    @Resource
    private TestPlanMapper testPlanMapper;
    @Resource
    private TestPlanConfigMapper testPlanConfigMapper;
    @Resource
    private TestPlanDocumentMapper testPlanDocumentMapper;
    @Lazy
    @Resource
    private TestPlanService testPlanService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Lazy
    @Resource
    private DefaultHubPlanImportService self;

    public DefaultHubJobResponse startImport(DefaultHubPlanImportRequest request, String operator) {
        String hubProjectId = defaultHubProjectService.getDefaultProjectId();
        if (StringUtils.isBlank(hubProjectId)) {
            throw new MSException("default hub project not configured");
        }
        TestPlan source = testPlanMapper.selectByPrimaryKey(request.getSourcePlanId());
        if (source == null || !StringUtils.equals(source.getProjectId(), hubProjectId)) {
            throw new MSException("source plan must belong to default hub project");
        }
        if (defaultHubProjectService.isDefaultProject(request.getTargetProjectId())) {
            throw new MSException("cannot import into default hub project");
        }
        String jobId = defaultHubSyncJobDao.createJob(DefaultHubConstants.JOB_TYPE_IMPORT_PLAN,
                request.getTargetProjectId(), operator);
        self.executeImportAsync(jobId, request, source, operator);
        DefaultHubJobResponse resp = new DefaultHubJobResponse();
        resp.setJobId(jobId);
        resp.setStatus(DefaultHubConstants.JOB_STATUS_PENDING);
        return resp;
    }

    @Async
    public void executeImportAsync(String jobId, DefaultHubPlanImportRequest request, TestPlan source, String operator) {
        if (!defaultHubSyncJobDao.tryAcquire(jobId)) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                importPlan(request, source, operator);
                defaultHubSyncJobDao.finish(jobId, DefaultHubConstants.JOB_STATUS_SUCCESS, 100, 1, 0, null);
            });
        } catch (Exception e) {
            LogUtils.error("default hub plan import failed, job=" + jobId, e);
            defaultHubSyncJobDao.finish(jobId, DefaultHubConstants.JOB_STATUS_FAILED, 0, 0, 1,
                    StringUtils.defaultString(e.getMessage()));
        }
    }

    private void importPlan(DefaultHubPlanImportRequest request, TestPlan source, String operator) {
        String targetModuleId = resolveTargetModule(request.getTargetProjectId());
        TestPlanExample example = new TestPlanExample();
        example.createCriteria().andProjectIdEqualTo(request.getTargetProjectId())
                .andNameEqualTo(source.getName()).andTypeEqualTo(TestPlanConstants.TEST_PLAN_TYPE_PLAN);
        List<TestPlan> exists = testPlanMapper.selectByExample(example);
        if (CollectionUtils.isNotEmpty(exists)) {
            if (DefaultHubConstants.CONFLICT_SKIP.equals(request.getConflictStrategy())) {
                return;
            }
            overwritePlan(exists.getFirst(), source, operator);
            return;
        }
        createPlan(source, request.getSourcePlanId(), request.getTargetProjectId(), targetModuleId, operator);
    }

    private void createPlan(TestPlan source, String hubPlanId, String targetProjectId, String moduleId, String operator) {
        String newId = IDGenerator.nextStr();
        TestPlan plan = new TestPlan();
        plan.setId(newId);
        plan.setProjectId(targetProjectId);
        plan.setModuleId(moduleId);
        plan.setGroupId(TestPlanConstants.TEST_PLAN_DEFAULT_GROUP_ID);
        plan.setName(source.getName());
        plan.setStatus(TestPlanConstants.TEST_PLAN_STATUS_NOT_ARCHIVED);
        plan.setType(TestPlanConstants.TEST_PLAN_TYPE_PLAN);
        plan.setTags(source.getTags());
        plan.setPlannedStartTime(source.getPlannedStartTime());
        plan.setPlannedEndTime(source.getPlannedEndTime());
        plan.setDescription(source.getDescription());
        plan.setNum(NumGenerator.nextNum(targetProjectId, ApplicationNumScope.TEST_PLAN));
        plan.setPos(testPlanService.getNextOrder(targetProjectId, TestPlanConstants.TEST_PLAN_DEFAULT_GROUP_ID));
        plan.setCreateUser(operator);
        plan.setUpdateUser(operator);
        long now = System.currentTimeMillis();
        plan.setCreateTime(now);
        plan.setUpdateTime(now);
        testPlanMapper.insert(plan);
        jdbcTemplate.update("UPDATE test_plan SET imported_from_hub_plan_id = ? WHERE id = ?", hubPlanId, newId);
        TestPlanConfig config = new TestPlanConfig();
        config.setTestPlanId(newId);
        testPlanConfigMapper.insertSelective(config);
        copyDocument(hubPlanId, newId, targetProjectId, operator);
    }

    private void overwritePlan(TestPlan target, TestPlan source, String operator) {
        TestPlan update = new TestPlan();
        update.setId(target.getId());
        update.setName(source.getName());
        update.setPlannedStartTime(source.getPlannedStartTime());
        update.setPlannedEndTime(source.getPlannedEndTime());
        update.setDescription(source.getDescription());
        update.setTags(source.getTags());
        update.setUpdateUser(operator);
        update.setUpdateTime(System.currentTimeMillis());
        testPlanMapper.updateByPrimaryKeySelective(update);
        jdbcTemplate.update("UPDATE test_plan SET imported_from_hub_plan_id = ? WHERE id = ?",
                source.getId(), target.getId());
        copyDocument(source.getId(), target.getId(), target.getProjectId(), operator);
    }

    private void copyDocument(String sourcePlanId, String targetPlanId, String targetProjectId, String operator) {
        TestPlanDocument doc = testPlanDocumentMapper.selectByTestPlanId(sourcePlanId);
        if (doc == null) {
            return;
        }
        // TODO: 图片文件拷贝到目标项目并重写 content 引用
        TestPlanDocument existing = testPlanDocumentMapper.selectByTestPlanId(targetPlanId);
        long now = System.currentTimeMillis();
        if (existing == null) {
            TestPlanDocument copy = new TestPlanDocument();
            copy.setId(IDGenerator.nextStr());
            copy.setTestPlanId(targetPlanId);
            copy.setProjectId(targetProjectId);
            copy.setContent(StringUtils.defaultString(doc.getContent()));
            copy.setContentType(doc.getContentType());
            copy.setCreateTime(now);
            copy.setUpdateTime(now);
            copy.setCreateUser(operator);
            copy.setUpdateUser(operator);
            testPlanDocumentMapper.insert(copy);
        } else {
            existing.setContent(StringUtils.defaultString(doc.getContent()));
            existing.setContentType(doc.getContentType());
            existing.setUpdateTime(now);
            existing.setUpdateUser(operator);
            testPlanDocumentMapper.updateByPrimaryKeySelective(existing);
        }
    }

    private String resolveTargetModule(String targetProjectId) {
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT id FROM test_plan_module WHERE project_id = ? AND parent_id = ? LIMIT 1",
                String.class, targetProjectId, ModuleConstants.ROOT_NODE_PARENT_ID);
        if (!ids.isEmpty()) {
            return ids.getFirst();
        }
        long now = System.currentTimeMillis();
        String id = IDGenerator.nextStr();
        jdbcTemplate.update(
                "INSERT INTO test_plan_module (id, project_id, name, parent_id, pos, create_time, update_time, create_user, update_user) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                id, targetProjectId, "默认模块", ModuleConstants.ROOT_NODE_PARENT_ID, 64L, now, now, "system", "system");
        return id;
    }
}
