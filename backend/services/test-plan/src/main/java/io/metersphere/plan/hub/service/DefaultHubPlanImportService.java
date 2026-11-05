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
    @Resource private io.metersphere.plan.service.PlanReviewCreationJournal creationJournal;
    @Resource private io.metersphere.functional.planreview.PlanReviewApplicationService relations;

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
        requireConflictStrategy(request);
        String hubProjectId = defaultHubProjectService.getDefaultProjectId();
        if (StringUtils.isBlank(hubProjectId)) {
            throw new MSException("default hub project not configured");
        }
        TestPlan source = testPlanMapper.selectByPrimaryKey(request.getSourcePlanId());
        if (source == null || !StringUtils.equals(source.getProjectId(), hubProjectId) || !TestPlanConstants.TEST_PLAN_TYPE_PLAN.equals(source.getType())) {
            throw new MSException("source plan must belong to default hub project");
        }
        if (defaultHubProjectService.isDefaultProject(request.getTargetProjectId())) {
            throw new MSException("cannot import into default hub project");
        }
        var reservation = creationJournal.prepare(request.getTargetProjectId(), operator, "HUB_IMPORT", "CREATE_PAIR", request.getRequestKey(),
                io.metersphere.sdk.util.JSON.toJSONString(request), request.getReviewSettings(), 0);
        boolean[] created = {false};
        String jobId = transactionTemplate.execute(status -> {
            jdbcTemplate.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE", request.getTargetProjectId());
            var operation = jdbcTemplate.queryForMap("SELECT * FROM plan_review_operation WHERE id=? FOR UPDATE", reservation.id());
            if (operation.get("result_json") != null) {
                var saved = io.metersphere.sdk.util.JSON.parseObject((String)operation.get("result_json"), java.util.Map.class);
                return (String) saved.get("jobId");
            }
            String id = defaultHubSyncJobDao.createJob(DefaultHubConstants.JOB_TYPE_IMPORT_PLAN, request.getTargetProjectId(), operator);
            jdbcTemplate.update("UPDATE plan_review_operation SET result_json=?,revision=revision+1,update_time=? WHERE id=?",
                    io.metersphere.sdk.util.JSON.toJSONString(java.util.Map.of("jobId", id)), System.currentTimeMillis(), reservation.id());
            created[0] = true;
            return id;
        });
        if (created[0]) self.executeImportAsync(jobId, request, source, operator, reservation);
        DefaultHubJobResponse resp = new DefaultHubJobResponse();
        resp.setJobId(jobId);
        var currentJob=defaultHubSyncJobDao.findById(jobId);
        resp.setStatus(currentJob==null?DefaultHubConstants.JOB_STATUS_PENDING:currentJob.getStatus());
        return resp;
    }

    @Async
    public void executeImportAsync(String jobId, DefaultHubPlanImportRequest request, TestPlan source, String operator,
            io.metersphere.plan.service.PlanReviewCreationJournal.Reservation reservation) {
        var observed=transactionTemplate.execute(status->{
            var operation=lockedOperation(request.getTargetProjectId(),reservation.id());
            if(!operator.equals(operation.get("executing_actor"))||java.util.Set.of("COMPLETED","SKIPPED","TERMINATED").contains(operation.get("state")))return null;
            if(!jobId.equals(lockedJob(operation))||!defaultHubSyncJobDao.tryAcquire(jobId))return null;
            return operation;
        });
        if(observed==null)return;
        try {
            transactionTemplate.executeWithoutResult(status -> {
                var current=lockedOperation(request.getTargetProjectId(),reservation.id());
                if(!sameExecution(current,observed)||"TERMINATED".equals(current.get("state")))return;
                importPlan(request, currentSource(request), operator, reservation);
                defaultHubSyncJobDao.finish(jobId, DefaultHubConstants.JOB_STATUS_SUCCESS, 100, 1, 0, null);
            });
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Default hub plan import paused job={} errorType={}",jobId,e.getClass().getSimpleName());
            recordFailure(observed);
        }
    }

    private java.util.Map<String,Object> lockedOperation(String project,String id){
        jdbcTemplate.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE",project);
        var rows=jdbcTemplate.queryForList("SELECT * FROM plan_review_operation WHERE id=? AND project_id=? AND source_kind='HUB_IMPORT' AND operation_type='CREATE_PAIR' FOR UPDATE",id,project);
        if(rows.size()!=1)throw new io.metersphere.functional.planreview.PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"原默认项目导入操作不可访问");return rows.getFirst();
    }
    private String lockedJob(java.util.Map<String,Object> operation){
        var result=io.metersphere.sdk.util.JSON.parseObject(java.util.Objects.toString(operation.get("result_json"),"{}"),java.util.Map.class);
        Object id=result.get("jobId");
        var rows=jdbcTemplate.queryForList("SELECT id FROM default_hub_sync_job WHERE id=? AND scope_project_id=? AND create_user=? AND job_type=? FOR UPDATE",String.class,id,operation.get("project_id"),operation.get("original_actor"),DefaultHubConstants.JOB_TYPE_IMPORT_PLAN);
        if(rows.size()!=1)throw new io.metersphere.functional.planreview.PlanReviewFailure("HUB_JOB_UNAVAILABLE",409,"原默认项目导入任务不可用，不会创建替代任务");return rows.getFirst();
    }
    private TestPlan currentSource(DefaultHubPlanImportRequest request){
        var source=creationJournal.readReservedPlan(request.getSourcePlanId());
        String hub=defaultHubProjectService.getDefaultProjectId();
        if(source==null||hub==null||!hub.equals(source.getProjectId())||!TestPlanConstants.TEST_PLAN_TYPE_PLAN.equals(source.getType())||defaultHubProjectService.isDefaultProject(request.getTargetProjectId()))throw new io.metersphere.functional.planreview.PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"原默认项目计划或目标范围已失效");return source;
    }
    private static boolean sameExecution(java.util.Map<String,Object> current,java.util.Map<String,Object> observed){
        return java.util.Objects.equals(current.get("executing_actor"),observed.get("executing_actor"))&&java.util.Objects.equals(current.get("executing_credential_id"),observed.get("executing_credential_id"))&&java.util.Objects.equals(current.get("revision"),observed.get("revision"));
    }
    void recordFailure(java.util.Map<String,Object> observed){
        transactionTemplate.executeWithoutResult(status->{
            var current=lockedOperation((String)observed.get("project_id"),(String)observed.get("id"));
            if(!sameExecution(current,observed)||java.util.Set.of("COMPLETED","SKIPPED","TERMINATED").contains(current.get("state")))return;
            String job=lockedJob(current),reason="默认项目计划导入未完成，原操作及已成功结果保留，请从操作记录恢复";
            jdbcTemplate.update("UPDATE plan_review_operation SET state='FAILED',safe_error=?,revision=revision+1,update_time=? WHERE id=?",reason,System.currentTimeMillis(),current.get("id"));
            defaultHubSyncJobDao.finish(job,DefaultHubConstants.JOB_STATUS_FAILED,0,0,1,reason);
        });
    }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void resumeReserved(java.util.Map<String,Object> operation,String actor){
        String job=lockedJob(operation);
        var request=io.metersphere.sdk.util.JSON.parseObject((String)operation.get("request_json"),DefaultHubPlanImportRequest.class);
        if(!java.util.Objects.equals(request.getTargetProjectId(),operation.get("project_id")))throw new io.metersphere.functional.planreview.PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"原导入目标不匹配");
        var reservation=new io.metersphere.plan.service.PlanReviewCreationJournal.Reservation((String)operation.get("id"),(String)operation.get("plan_id"),(String)operation.get("review_id"),(String)operation.get("state"));
        importPlan(request,currentSource(request),actor,reservation);
        defaultHubSyncJobDao.finish(job,DefaultHubConstants.JOB_STATUS_SUCCESS,100,1,0,null);
    }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void handoffReserved(java.util.Map<String,Object> operation){
        String job=lockedJob(operation);
        jdbcTemplate.update("UPDATE default_hub_sync_job SET status=?,error_message=NULL,update_time=? WHERE id=? AND scope_project_id=? AND status<>?",DefaultHubConstants.JOB_STATUS_PENDING,System.currentTimeMillis(),job,operation.get("project_id"),DefaultHubConstants.JOB_STATUS_SUCCESS);
    }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void terminateReserved(java.util.Map<String,Object> operation){
        String job=lockedJob(operation);
        jdbcTemplate.update("UPDATE default_hub_sync_job SET status=?,error_message=?,update_time=? WHERE id=? AND scope_project_id=? AND status<>?",DefaultHubConstants.JOB_STATUS_FAILED,"原操作已终止，已成功对象保持不变",System.currentTimeMillis(),job,operation.get("project_id"),DefaultHubConstants.JOB_STATUS_SUCCESS);
    }

    private void importPlan(DefaultHubPlanImportRequest request, TestPlan source, String operator,
            io.metersphere.plan.service.PlanReviewCreationJournal.Reservation reservation) {
        requireConflictStrategy(request);
        if (creationJournal.lockAndExists(reservation, request.getTargetProjectId(), operator)) return;
        TestPlanExample example = new TestPlanExample();
        example.createCriteria().andProjectIdEqualTo(request.getTargetProjectId())
                .andNameEqualTo(source.getName()).andTypeEqualTo(TestPlanConstants.TEST_PLAN_TYPE_PLAN);
        List<TestPlan> exists = testPlanMapper.selectByExample(example);
        if (CollectionUtils.isNotEmpty(exists)) {
            if (DefaultHubConstants.CONFLICT_SKIP.equals(request.getConflictStrategy())) {
                jdbcTemplate.update("UPDATE plan_review_operation SET stage='COMPLETED',state='SKIPPED',revision=revision+1,update_time=? WHERE id=?",System.currentTimeMillis(),reservation.id());
                return;
            }
            creationJournal.requireOverwrite(request.getTargetProjectId(),exists.getFirst().getId(),operator);
            relations.requireSourceEditable(exists.getFirst().getId());
            overwritePlan(exists.getFirst(), source, operator);
            jdbcTemplate.update("UPDATE plan_review_operation SET stage='COMPLETED',state='COMPLETED',revision=revision+1,update_time=? WHERE id=?",System.currentTimeMillis(),reservation.id());
            return;
        }
        String targetModuleId = resolveTargetModule(request.getTargetProjectId());
        creationJournal.requirePlanDestination(request.getTargetProjectId(),TestPlanConstants.TEST_PLAN_DEFAULT_GROUP_ID,targetModuleId,TestPlanConstants.TEST_PLAN_TYPE_PLAN,operator);
        createPlan(source, request.getSourcePlanId(), request.getTargetProjectId(), targetModuleId, operator, reservation, request.getReviewSettings());
    }
    private static void requireConflictStrategy(DefaultHubPlanImportRequest request){
        if(!java.util.Set.of(DefaultHubConstants.CONFLICT_SKIP,DefaultHubConstants.CONFLICT_OVERWRITE).contains(java.util.Objects.toString(request.getConflictStrategy(),"")))throw new io.metersphere.functional.planreview.PlanReviewFailure("INVALID_CONFLICT_STRATEGY",400,"请选择跳过或覆盖，未知策略不会执行");
    }

    private void createPlan(TestPlan source, String hubPlanId, String targetProjectId, String moduleId, String operator,
            io.metersphere.plan.service.PlanReviewCreationJournal.Reservation reservation,
            io.metersphere.functional.planreview.PlanReviewSettings settings) {
        String newId = reservation.planId();
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
        insertPlanConfig(newId, hubPlanId);
        copyDocument(hubPlanId, newId, targetProjectId, operator);
        testPlanService.initDefaultPlanCollection(newId, operator);
        creationJournal.complete(reservation, targetProjectId, plan.getName(), operator, settings);
    }

    private void insertPlanConfig(String newPlanId, String sourcePlanId) {
        TestPlanConfig source = testPlanConfigMapper.selectByPrimaryKey(sourcePlanId);
        TestPlanConfig config = new TestPlanConfig();
        config.setTestPlanId(newPlanId);
        if (source != null) {
            config.setAutomaticStatusUpdate(Boolean.TRUE.equals(source.getAutomaticStatusUpdate()));
            config.setRepeatCase(Boolean.TRUE.equals(source.getRepeatCase()));
            config.setPassThreshold(source.getPassThreshold() != null ? source.getPassThreshold() : 100.0);
            config.setCaseRunMode(StringUtils.defaultIfBlank(source.getCaseRunMode(), "PARALLEL"));
        } else {
            config.setAutomaticStatusUpdate(false);
            config.setRepeatCase(false);
            config.setPassThreshold(100.0);
            config.setCaseRunMode("PARALLEL");
        }
        testPlanConfigMapper.insertSelective(config);
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
