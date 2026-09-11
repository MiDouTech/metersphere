package io.metersphere.agent.service;

import io.metersphere.agent.constants.*;
import io.metersphere.agent.dto.*;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.bug.dto.request.BugTransitionRequest;
import io.metersphere.bug.dto.response.BugTransitionDTO;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.dto.request.BasePlanCaseBatchRequest;
import io.metersphere.plan.dto.request.TestPlanUpdateRequest;
import io.metersphere.plan.dto.response.TestPlanAssociationResponse;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.plan.mapper.TestPlanFunctionalCaseMapper;
import io.metersphere.plan.service.TestPlanService;
import io.metersphere.plan.service.TestPlanFunctionalCaseService;
import io.metersphere.sdk.constants.*;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.LogInsertModule;
import io.metersphere.system.service.PermissionCheckService;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Business boundaries for the additional personal MCP operations. */
@Service
public class AgentMcpMaintenanceService {
    @Resource private Validator validator;
    @Resource(name = "agentProjectService") private AgentProjectService projectService;
    @Resource private PermissionCheckService permissionCheckService;
    @Resource private AgentBatchSubmitService batchService;
    @Resource private FunctionalCaseMapper functionalCaseMapper;
    @Resource private io.metersphere.system.mapper.AgentExecAttachmentMapper attachmentMapper;
    @Resource private TestPlanFunctionalCaseMapper planCaseMapper;
    @Resource private TestPlanMapper planMapper;
    @Resource private TestPlanService planService;
    @Resource private TestPlanFunctionalCaseService planCaseService;
    @Resource private AgentTestPlanWriteService planWriteService;
    @Resource private AgentTaskClaimService taskClaims;
    @Resource private AgentExecutionCheckpointService checkpointService;
    @Resource private AgentExecutionMapper executionMapper;
    @Resource(name = "agentBugWriteService") private AgentBugWriteService bugService;
    @Resource private AgentExecLogService execLogService;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private io.metersphere.plan.service.TestPlanManagementService planManagementService;

    public AgentTestPlanDTO getPlan(String planId) {
        TestPlan plan = planMapper.selectByPrimaryKey(required(planId));
        if (plan == null) throw new MSException("RESOURCE_NOT_FOUND");
        project(plan.getProjectId(), PermissionConstants.TEST_PLAN_READ);
        return planWriteService.get(planId);
    }

    public BugTransitionDTO transitions(String projectId, String bugId) {
        String project = project(projectId, PermissionConstants.PROJECT_BUG_READ);
        return bugService.getTransitions(project, required(bugId));
    }

    public BugTransitionDTO transition(String projectId, String bugId, BugTransitionRequest request) {
        validate(request);
        if (request.getExpectedUpdateTime() < 0) throw new MSException("VALIDATION_ERROR");
        String project = project(projectId, PermissionConstants.PROJECT_BUG_UPDATE);
        return bugService.transition(project, required(bugId), request);
    }

    @Transactional(readOnly = true)
    public AgentExecutionTaskSearchResponse history(AgentExecutionHistoryRequest request) {
        validate(request);
        if (request.getCreatedAfter() != null && request.getCreatedBefore() != null
                && request.getCreatedAfter() > request.getCreatedBefore()) throw new MSException("VALIDATION_ERROR");
        String status = StringUtils.upperCase(StringUtils.trimToNull(request.getStatus()));
        Set<String> statuses = new HashSet<>(AgentExecutionStatus.TERMINAL);
        statuses.addAll(AgentExecutionStatus.HOLDING);
        statuses.addAll(List.of(AgentExecutionStatus.CREATED, AgentExecutionStatus.RUNNING, AgentExecutionStatus.WRITING_BACK));
        if (status != null && !statuses.contains(status)) throw new MSException("VALIDATION_ERROR");
        request.setStatus(status);
        request.setKeyword(StringUtils.trimToEmpty(request.getKeyword()));
        String project = project(request.getProjectId(), PermissionConstants.AI_EXECUTION_READ);
        // This query declares ESCAPE '!' (the project-search helper uses backslashes).
        String like = request.getKeyword().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        AgentExecutionTaskSearchResponse response = new AgentExecutionTaskSearchResponse();
        response.setCurrent(request.getCurrent());
        response.setPageSize(request.getPageSize());
        response.setTotal(executionMapper.countPersonalHistory(project, request, like));
        response.setItems(executionMapper.searchPersonalHistory(project, request, like,
                (request.getCurrent() - 1) * request.getPageSize()));
        return response;
    }

    public AgentExecutionCheckpointDTO resume(String taskId, String checkpointId, AgentCheckpointResumeRequest request) {
        validate(request);
        var task = taskClaims.getPersonalTask(required(taskId));
        project(task.getProjectId(), PermissionConstants.AI_EXECUTION_RUN);
        String previous = AgentExecutionActorContext.get();
        AgentExecutionActorContext.bind(actor());
        try {
            return checkpointService.resume(taskId, required(checkpointId), request);
        } finally {
            if (previous == null) AgentExecutionActorContext.clear();
            else AgentExecutionActorContext.bind(previous);
        }
    }

    public AgentBatchSubmitResponse batch(AgentBatchSubmitRequest request, String requestId) {
        required(requestId);
        if (request == null || request.getResults() == null || request.getResults().isEmpty()
                || request.getResults().size() > 100) throw new MSException("VALIDATION_ERROR");
        // Authorize the whole batch before any side effects. Business failures remain per-item.
        String project = project(request.getProjectId(), PermissionConstants.FUNCTIONAL_CASE_READ_UPDATE);
        request.setProjectId(project);
        for (AgentCaseSubmitRequest item : request.getResults()) {
            if (item == null) throw new MSException("VALIDATION_ERROR");
            if (StringUtils.isBlank(item.getProjectId())) item.setProjectId(project);
            if (!project.equals(projectService.resolveProjectId(item.getProjectId()))) throw new MSException("RESOURCE_PROJECT_MISMATCH");
            item.setProjectId(project);
            if (StringUtils.isBlank(item.getTestPlanId())) item.setTestPlanId(request.getTestPlanId());
            if (StringUtils.isBlank(item.getExecutionTaskId())) item.setExecutionTaskId(request.getExecutionTaskId());
            validate(item);
            if (!Set.of("SUCCESS", "ERROR", "FAKE_ERROR", "BLOCKED").contains(item.getLastExecResult())) throw new MSException("VALIDATION_ERROR");
            var functionalCase = functionalCaseMapper.selectByPrimaryKey(item.getCaseId());
            if (functionalCase == null || Boolean.TRUE.equals(functionalCase.getDeleted())
                    || !project.equals(functionalCase.getProjectId())) throw new MSException("RESOURCE_NOT_FOUND");
            if (StringUtils.isNotBlank(item.getTestPlanId()) != StringUtils.isNotBlank(item.getTestPlanCaseId())) throw new MSException("VALIDATION_ERROR");
            if (StringUtils.isNotBlank(item.getTestPlanId())) {
                requirePlan(project, item.getTestPlanId(), PermissionConstants.TEST_PLAN_READ_EXECUTE);
                var relation = planCaseMapper.selectByPrimaryKey(item.getTestPlanCaseId());
                if (relation == null || !item.getTestPlanId().equals(relation.getTestPlanId())
                        || !item.getCaseId().equals(relation.getFunctionalCaseId())) throw new MSException("RESOURCE_PROJECT_MISMATCH");
            }
            if (StringUtils.isNotBlank(item.getExecutionTaskId())) {
                var task = taskClaims.getPersonalTask(item.getExecutionTaskId());
                if (!project.equals(task.getProjectId())) throw new MSException("RESOURCE_PROJECT_MISMATCH");
                boolean inScope = executionMapper.selectCasesByTaskId(task.getId()).stream().anyMatch(snapshot ->
                        item.getCaseId().equals(snapshot.getCaseId())
                        && StringUtils.equals(StringUtils.trimToNull(item.getTestPlanId()), StringUtils.trimToNull(snapshot.getTestPlanId()))
                        && StringUtils.equals(StringUtils.trimToNull(item.getTestPlanCaseId()), StringUtils.trimToNull(snapshot.getTestPlanCaseId())));
                if (!inScope) throw new MSException("RESOURCE_PROJECT_MISMATCH");
            }
            if (item.getAttachmentIds() != null) {
                if (item.getAttachmentIds().size() > 100) throw new MSException("VALIDATION_ERROR");
                for (String id : item.getAttachmentIds()) {
                    var attachment = attachmentMapper.selectByPrimaryKey(required(id));
                    if (attachment == null || !actor().equals(attachment.getCreateUser())) throw new MSException("RESOURCE_NOT_FOUND");
                }
            }
        }
        validate(request);
        return batchService.batchSubmit(request, requestId);
    }

    private static final Set<String> PLAN_PATCH_FIELDS = Set.of("name", "description", "tags", "plannedStartTime",
            "plannedEndTime", "automaticStatusUpdate", "repeatCase", "passThreshold");

    @Transactional(rollbackFor = Exception.class)
    public AgentTestPlanDTO updatePlan(String projectId, String testPlanId, Long expectedUpdateTime, Map<String,Object> patch) {
        if (expectedUpdateTime == null || expectedUpdateTime < 0 || expectedUpdateTime == Long.MAX_VALUE || patch == null || patch.isEmpty() || !PLAN_PATCH_FIELDS.containsAll(patch.keySet())
                || patch.values().stream().anyMatch(Objects::isNull)) throw new MSException("VALIDATION_ERROR");
        TestPlan plan = lockEditablePlan(projectId, testPlanId, PermissionConstants.TEST_PLAN_READ_UPDATE);
        if (!expectedUpdateTime.equals(plan.getUpdateTime())) throw new MSException("VERSION_CONFLICT");
        TestPlanUpdateRequest request = JSON.parseObject(JSON.toJSONString(patch), TestPlanUpdateRequest.class);
        request.setId(plan.getId());
        request.setModuleId(null);
        request.setGroupId(null);
        if (!patch.containsKey("tags")) request.setTags(new LinkedHashSet<>(plan.getTags() == null ? List.of() : plan.getTags()));
        if (patch.containsKey("name") && StringUtils.isBlank(request.getName())) throw new MSException("VALIDATION_ERROR");
        Long start = request.getPlannedStartTime() == null ? plan.getPlannedStartTime() : request.getPlannedStartTime();
        Long end = request.getPlannedEndTime() == null ? plan.getPlannedEndTime() : request.getPlannedEndTime();
        if ((start != null && start < 0) || (end != null && end < 0)) throw new MSException("VALIDATION_ERROR");
        if (start != null && end != null && start > end) throw new MSException("VALIDATION_ERROR");
        validate(request);
        planService.update(request, actor(), "/api/mcp/test-plan/update", "POST");
        TestPlan version = new TestPlan();
        version.setId(plan.getId());
        version.setUpdateTime(Math.max(System.currentTimeMillis(), expectedUpdateTime + 1));
        version.setUpdateUser(actor());
        planMapper.updateByPrimaryKeySelective(version);
        execLogService.audit("TEST_PLAN_UPDATE", plan.getId(), JSON.toJSONString(patch));
        return planWriteService.get(plan.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public TestPlanAssociationResponse disassociate(String projectId, String testPlanId, List<String> associationIds) {
        if (associationIds == null || associationIds.isEmpty() || associationIds.size() > 100
                || associationIds.stream().anyMatch(StringUtils::isBlank)
                || new HashSet<>(associationIds).size() != associationIds.size()) throw new MSException("VALIDATION_ERROR");
        TestPlan plan = lockEditablePlan(projectId, testPlanId, PermissionConstants.TEST_PLAN_READ_ASSOCIATION);
        for (String id : associationIds) {
            var relation = planCaseMapper.selectByPrimaryKey(id);
            if (relation == null || !testPlanId.equals(relation.getTestPlanId())) throw new MSException("RESOURCE_PROJECT_MISMATCH");
        }
        BasePlanCaseBatchRequest request = new BasePlanCaseBatchRequest();
        request.setProjectId(plan.getProjectId());
        request.setTestPlanId(testPlanId);
        request.setSelectAll(false);
        request.setSelectIds(associationIds);
        var response = planCaseService.disassociate(request, new LogInsertModule(actor(), "/api/mcp/test-plan/disassociate", "POST"));
        execLogService.audit("TEST_PLAN_DISASSOCIATE", testPlanId, JSON.toJSONString(associationIds));
        return response;
    }

    private TestPlan lockEditablePlan(String projectId, String testPlanId, String permission) {
        TestPlan authorized = requirePlan(projectId, testPlanId, permission);
        jdbcTemplate.queryForObject("SELECT id FROM test_plan WHERE id=? FOR UPDATE", String.class, authorized.getId());
        TestPlan plan = requirePlan(projectId, testPlanId, permission);
        planManagementService.checkModuleIsOpen(testPlanId,
                io.metersphere.plan.constants.TestPlanResourceConfig.CHECK_TYPE_TEST_PLAN,
                PermissionConstants.TEST_PLAN_READ_ASSOCIATION.equals(permission)
                        ? List.of(io.metersphere.plan.constants.TestPlanResourceConfig.CONFIG_TEST_PLAN_FUNCTIONAL_CASE)
                        : List.of(io.metersphere.plan.constants.TestPlanResourceConfig.CONFIG_TEST_PLAN));
        Long running = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_plan_report WHERE test_plan_id=? AND deleted=0 AND exec_status IN ('RUNNING','RERUNNING','PENDING')", Long.class, testPlanId);
        Long aiRunning = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_execution_task WHERE test_plan_id=? AND status NOT IN ('SUCCESS','PARTIAL_SUCCESS','FAILED','CANCELED','EXPIRED')", Long.class, testPlanId);
        if ((running != null && running > 0) || (aiRunning != null && aiRunning > 0)) throw new MSException("TEST_PLAN_EXECUTING");
        return plan;
    }

    private TestPlan requirePlan(String projectId, String planId, String permission) {
        String project = project(projectId, permission);
        TestPlan plan = planMapper.selectByPrimaryKey(required(planId));
        if (plan == null || !project.equals(plan.getProjectId()) || !TestPlanConstants.TEST_PLAN_TYPE_PLAN.equals(plan.getType())) throw new MSException("RESOURCE_NOT_FOUND");
        if (TestPlanConstants.TEST_PLAN_STATUS_ARCHIVED.equals(plan.getStatus())) throw new MSException("TEST_PLAN_ARCHIVED");
        return plan;
    }

    private String project(String projectId, String permission) {
        String project = projectService.resolveProjectId(required(projectId));
        if (!permissionCheckService.userHasSourcePermission(actor(), project, permission, "PROJECT")) throw new MSException("PERMISSION_DENIED");
        return project;
    }

    private String actor() {
        var token = AgentTokenContext.get();
        if (token == null || StringUtils.isBlank(token.getUserId())) throw new MSException("AUTHENTICATION_REQUIRED");
        return token.getUserId();
    }

    private String required(String value) {
        if (StringUtils.isBlank(value)) throw new MSException("VALIDATION_ERROR");
        return value;
    }

    private void validate(Object request) {
        if (request == null || !validator.validate(request).isEmpty()) throw new MSException("VALIDATION_ERROR");
    }
}
