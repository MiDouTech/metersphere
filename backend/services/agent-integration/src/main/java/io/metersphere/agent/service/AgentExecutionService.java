package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.agent.constants.AgentExecutionStatus;
import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.dto.AgentCaseSearchRequest;
import io.metersphere.agent.dto.AgentCaseSearchResponse;
import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionCreateRequest;
import io.metersphere.agent.dto.AgentExecutionEventDTO;
import io.metersphere.agent.dto.AgentExecutionEventsRequest;
import io.metersphere.agent.dto.AgentExecutionEventsResponse;
import io.metersphere.agent.dto.AgentExecutionResolveRequest;
import io.metersphere.agent.dto.AgentExecutionResolveResponse;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentTestPlanDTO;
import io.metersphere.agent.dto.AgentTestPlanSearchRequest;
import io.metersphere.agent.dto.AgentTestPlanSearchResponse;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.domain.TestPlanFunctionalCase;
import io.metersphere.plan.domain.TestPlanFunctionalCaseExample;
import io.metersphere.plan.mapper.TestPlanFunctionalCaseMapper;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.constants.ExecStatus;
import io.metersphere.sdk.constants.ResultStatus;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentExecutionService {
    private static final int DEFAULT_CONFIRM_THRESHOLD = 20;
    private static final int EVENT_LIMIT_MAX = 500;

    @Resource
    private AgentExecutionMapper agentExecutionMapper;
    @Resource
    private AgentProjectService agentProjectService;
    @Resource
    private AgentTestPlanQueryService agentTestPlanQueryService;
    @Resource
    private AgentFunctionalCaseSearchService agentFunctionalCaseSearchService;
    @Resource
    private FunctionalCaseMapper functionalCaseMapper;
    @Resource
    private TestPlanMapper testPlanMapper;
    @Resource
    private TestPlanFunctionalCaseMapper testPlanFunctionalCaseMapper;
    @Resource
    private ProjectMapper projectMapper;

    public AgentExecutionResolveResponse resolve(AgentExecutionResolveRequest request) {
        AgentExecutionResolveRequest actual = request == null ? new AgentExecutionResolveRequest() : request;
        AgentExecutionResolveResponse response = new AgentExecutionResolveResponse();
        int threshold = normalizeThreshold(actual.getThreshold());
        if (StringUtils.isBlank(actual.getProjectId())) {
            response.setStatus(AgentExecutionStatus.WAITING_CONFIRMATION);
            response.setExecutable(false);
            response.setConfirmationRequired(true);
            response.setConfirmationReason("缺少项目，请明确项目 ID、项目编号或项目名称");
            response.setMessage(response.getConfirmationReason());
            return response;
        }

        String projectId = agentProjectService.resolveProjectId(actual.getProjectId());
        response.setProjectId(projectId);
        if (CollectionUtils.isNotEmpty(actual.getCaseIds())) {
            List<AgentCaseDTO> cases = validateAndMapCases(projectId, actual.getCaseIds(), actual.getTestPlanId());
            fillResolvedCases(response, cases, actual.getTestPlanId(), threshold, "已按显式 caseId 固定执行范围");
            return response;
        }

        String testPlanId = StringUtils.trimToEmpty(actual.getTestPlanId());
        if (StringUtils.isBlank(testPlanId) && StringUtils.isNotBlank(actual.getTestPlanName())) {
            AgentTestPlanSearchRequest searchRequest = new AgentTestPlanSearchRequest();
            searchRequest.setProjectId(projectId);
            searchRequest.setKeyword(actual.getTestPlanName());
            searchRequest.setIncludeArchived(false);
            searchRequest.setPageSize(AgentConstants.MAX_PAGE_SIZE);
            AgentTestPlanSearchResponse search = agentTestPlanQueryService.search(searchRequest);
            List<AgentTestPlanDTO> exact = search.getItems().stream()
                    .filter(item -> StringUtils.equalsIgnoreCase(item.getName(), actual.getTestPlanName())
                            || StringUtils.equals(item.getId(), actual.getTestPlanName())
                            || StringUtils.equals(String.valueOf(item.getNum()), actual.getTestPlanName()))
                    .toList();
            if (exact.size() == 1) {
                testPlanId = exact.get(0).getId();
            } else if (exact.size() > 1) {
                response.setCandidatePlans(exact);
                response.setStatus(AgentExecutionStatus.WAITING_CONFIRMATION);
                response.setExecutable(false);
                response.setConfirmationRequired(true);
                response.setConfirmationReason("测试计划匹配到多个候选项，请明确 testPlanId");
                response.setMessage(response.getConfirmationReason());
                return response;
            }
        }

        if (StringUtils.isNotBlank(testPlanId)) {
            assertPlanBelongsToProject(projectId, testPlanId);
            List<AgentCaseDTO> cases = searchPlanCases(projectId, testPlanId, threshold + 1, false);
            fillResolvedCases(response, cases, testPlanId, threshold, "已按测试计划固定执行范围");
            return response;
        }

        AgentTestPlanSearchRequest searchRequest = new AgentTestPlanSearchRequest();
        searchRequest.setProjectId(projectId);
        searchRequest.setKeyword(actual.getQuery());
        searchRequest.setIncludeArchived(false);
        searchRequest.setPageSize(10);
        AgentTestPlanSearchResponse plans = agentTestPlanQueryService.search(searchRequest);
        List<AgentTestPlanDTO> runnablePlans = plans.getItems().stream()
                .filter(item -> item.getAssociatedCaseCount() != null && item.getAssociatedCaseCount() > 0)
                .toList();
        if (runnablePlans.size() == 1) {
            testPlanId = runnablePlans.get(0).getId();
            List<AgentCaseDTO> cases = searchPlanCases(projectId, testPlanId, threshold + 1, false);
            fillResolvedCases(response, cases, testPlanId, threshold, "已按唯一可执行测试计划固定执行范围");
            return response;
        }
        if (runnablePlans.size() > 1) {
            response.setCandidatePlans(runnablePlans);
            response.setStatus(AgentExecutionStatus.WAITING_CONFIRMATION);
            response.setExecutable(false);
            response.setConfirmationRequired(true);
            response.setConfirmationReason("存在多个可执行测试计划，请确认后再创建执行任务");
            response.setMessage(response.getConfirmationReason());
            return response;
        }

        String keyword = StringUtils.defaultIfBlank(actual.getCaseKeyword(), actual.getQuery());
        long total = agentExecutionMapper.countProjectCases(projectId, StringUtils.trimToEmpty(keyword),
                AgentProjectService.escapeLike(StringUtils.trimToEmpty(keyword).toLowerCase()));
        List<AgentExecutionCaseDTO> fallbackCases = agentExecutionMapper.selectProjectCases(projectId, StringUtils.trimToEmpty(keyword),
                AgentProjectService.escapeLike(StringUtils.trimToEmpty(keyword).toLowerCase()), threshold + 1);
        response.setStatus(AgentExecutionStatus.WAITING_CONFIRMATION);
        response.setExecutable(total > 0);
        response.setConfirmationRequired(true);
        response.setConfirmationReason("未找到可自动选择的测试计划，需确认是否改为执行项目下有效功能用例");
        response.setMessage(response.getConfirmationReason());
        response.setTotal((int) total);
        response.setCases(fallbackCases.stream().map(this::toAgentCase).toList());
        return response;
    }

    public AgentExecutionTaskDTO create(AgentExecutionCreateRequest request) {
        if (request == null || StringUtils.isBlank(request.getProjectId())) {
            throw new MSException("缺少 projectId");
        }
        String projectId = agentProjectService.resolveProjectId(request.getProjectId());
        String userId = requireUserId();
        if (StringUtils.isNotBlank(request.getIdempotencyKey())) {
            AgentExecutionTaskDTO existing = agentExecutionMapper.selectTaskByIdempotency(projectId, userId, request.getIdempotencyKey());
            if (existing != null) {
                return hydrate(existing);
            }
        }

        String testPlanId = StringUtils.trimToNull(request.getTestPlanId());
        if (StringUtils.isNotBlank(testPlanId)) {
            assertPlanBelongsToProject(projectId, testPlanId);
        }
        List<AgentExecutionCaseDTO> cases = resolveCreateCases(projectId, testPlanId, request.getCaseIds());
        if (CollectionUtils.isEmpty(cases)) {
            throw new MSException("未解析到可执行功能用例，任务未创建");
        }

        boolean confirmRequired = cases.size() > DEFAULT_CONFIRM_THRESHOLD && !BooleanUtils.isTrue(request.getConfirmed());
        String status = confirmRequired ? AgentExecutionStatus.WAITING_CONFIRMATION : AgentExecutionStatus.CREATED;
        Project project = projectMapper.selectByPrimaryKey(projectId);
        long now = System.currentTimeMillis();
        AgentExecutionTaskDTO task = new AgentExecutionTaskDTO();
        task.setId(IDGenerator.nextStr());
        task.setOrganizationId(project == null ? null : project.getOrganizationId());
        task.setProjectId(projectId);
        task.setTestPlanId(testPlanId);
        task.setSource(StringUtils.defaultIfBlank(request.getSource(), "API"));
        task.setStatus(status);
        task.setRunnerId(request.getRunnerId());
        task.setProviderId(request.getProviderId());
        task.setEnvironmentId(request.getEnvironmentId());
        task.setTargetUrl(request.getTargetUrl());
        task.setBrowserType(request.getBrowserType());
        task.setLoginMode(request.getLoginMode());
        task.setIdempotencyKey(StringUtils.trimToNull(request.getIdempotencyKey()));
        task.setConfirmRequired(confirmRequired);
        task.setConfirmationReason(confirmRequired ? "执行范围超过 " + DEFAULT_CONFIRM_THRESHOLD + " 条，需要确认后继续" : null);
        task.setTotalCount(cases.size());
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setBlockedCount(0);
        task.setSkippedCount(0);
        task.setUnexecutedCount(cases.size());
        task.setExecutedBy(StringUtils.defaultIfBlank(request.getExecutedBy(), userId));
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setCreateUser(userId);
        task.setUpdateUser(userId);
        agentExecutionMapper.insertTask(task);

        int pos = 0;
        for (AgentExecutionCaseDTO item : cases) {
            item.setId(IDGenerator.nextStr());
            item.setTaskId(task.getId());
            item.setProjectId(projectId);
            item.setTestPlanId(testPlanId);
            item.setStatus(AgentExecutionStatus.CREATED);
            item.setResult(null);
            item.setPos(pos++);
            item.setRetryCount(0);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            agentExecutionMapper.insertCase(item);
        }
        appendEvent(task.getId(), null, "INFO", "TASK_CREATED", "AI 执行任务已创建", Map.of(
                "total", cases.size(),
                "confirmRequired", confirmRequired
        ));
        return get(task.getId());
    }

    public AgentExecutionTaskDTO get(String id) {
        AgentExecutionTaskDTO task = requireTask(id);
        return hydrate(task);
    }

    public AgentExecutionEventsResponse events(String id, AgentExecutionEventsRequest request) {
        requireTask(id);
        long cursor = request == null || request.getCursor() == null ? 0L : request.getCursor();
        int limit = request == null || request.getLimit() == null ? 100 : Math.min(Math.max(request.getLimit(), 1), EVENT_LIMIT_MAX);
        List<AgentExecutionEventDTO> events = agentExecutionMapper.selectEvents(id, cursor, limit);
        AgentExecutionEventsResponse response = new AgentExecutionEventsResponse();
        response.setEvents(events);
        long nextCursor = events.isEmpty() ? cursor : events.get(events.size() - 1).getSequence();
        response.setCursor(nextCursor);
        Long max = agentExecutionMapper.selectMaxEventSequence(id);
        response.setHasMore(max != null && max > nextCursor);
        return response;
    }

    public AgentExecutionTaskDTO confirm(String id, String reason) {
        AgentExecutionTaskDTO task = requireTask(id);
        assertNotTerminal(task);
        agentExecutionMapper.confirmTask(id, AgentExecutionStatus.PREPARING_BROWSER, requireUserId(), System.currentTimeMillis());
        appendEvent(id, null, "INFO", "TASK_CONFIRMED", StringUtils.defaultIfBlank(reason, "任务范围已确认"), null);
        return get(id);
    }

    public AgentExecutionTaskDTO loginReady(String id, String reason) {
        AgentExecutionTaskDTO task = requireTask(id);
        assertNotTerminal(task);
        agentExecutionMapper.updateTaskStatus(id, AgentExecutionStatus.RUNNING, requireUserId(), System.currentTimeMillis());
        appendEvent(id, null, "INFO", "LOGIN_READY", StringUtils.defaultIfBlank(reason, "登录已恢复，可继续执行"), null);
        return get(id);
    }

    public AgentExecutionTaskDTO cancel(String id, String reason) {
        AgentExecutionTaskDTO task = requireTask(id);
        if (AgentExecutionStatus.CANCELED.equals(task.getStatus())) {
            return hydrate(task);
        }
        if (!AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
            agentExecutionMapper.updateTaskStatus(id, AgentExecutionStatus.CANCELED, requireUserId(), System.currentTimeMillis());
            appendEvent(id, null, "WARN", "TASK_CANCELED", StringUtils.defaultIfBlank(reason, "任务已取消"), null);
        }
        return get(id);
    }

    public AgentExecutionTaskDTO retry(String id, String reason) {
        AgentExecutionTaskDTO task = requireTask(id);
        List<AgentExecutionCaseDTO> retryCases = agentExecutionMapper.selectCasesByTaskIdAndStatuses(id,
                List.of(AgentExecutionStatus.FAILED, AgentExecutionStatus.CASE_BLOCKED));
        if (CollectionUtils.isEmpty(retryCases)) {
            throw new MSException("当前任务没有失败或阻塞用例可重试");
        }
        long now = System.currentTimeMillis();
        agentExecutionMapper.retryFailedCases(id, now);
        agentExecutionMapper.updateTaskStatus(id, AgentExecutionStatus.CREATED, requireUserId(), now);
        appendEvent(id, null, "INFO", "TASK_RETRY", StringUtils.defaultIfBlank(reason, "失败/阻塞用例已进入重试队列"), Map.of("retryCases", retryCases.size()));
        refreshCounts(id);
        return get(id);
    }

    public void markCaseWritebackSuccess(String taskId, String caseId, String result) {
        if (StringUtils.isAnyBlank(taskId, caseId)) {
            return;
        }
        AgentExecutionTaskDTO task = requireTask(taskId);
        if (AgentExecutionStatus.CANCELED.equals(task.getStatus())) {
            throw new MSException("执行任务已取消，禁止继续回写结果");
        }
        String caseStatus = toCaseStatus(result);
        agentExecutionMapper.updateCaseStatus(taskId, caseId, caseStatus, result, null, System.currentTimeMillis());
        appendEvent(taskId, caseId, "INFO", "CASE_WRITEBACK_SUCCESS", "用例结果已回写：" + result, null);
        refreshCounts(taskId);
    }

    public void markCaseWritebackFailed(String taskId, String caseId, String message) {
        if (StringUtils.isAnyBlank(taskId, caseId)) {
            return;
        }
        requireTask(taskId);
        agentExecutionMapper.updateCaseStatus(taskId, caseId, AgentExecutionStatus.FAILED, null,
                StringUtils.abbreviate(message, 1000), System.currentTimeMillis());
        appendEvent(taskId, caseId, "ERROR", "CASE_WRITEBACK_FAILED", StringUtils.defaultString(message, "结果回写失败"), null);
        refreshCounts(taskId);
    }

    private void refreshCounts(String taskId) {
        List<AgentExecutionCaseDTO> cases = agentExecutionMapper.selectCasesByTaskId(taskId);
        int success = 0;
        int failed = 0;
        int blocked = 0;
        int skipped = 0;
        int unexecuted = 0;
        for (AgentExecutionCaseDTO item : cases) {
            if (AgentExecutionStatus.SUCCESS.equals(item.getStatus())) {
                success++;
            } else if (AgentExecutionStatus.FAILED.equals(item.getStatus())) {
                failed++;
            } else if (AgentExecutionStatus.CASE_BLOCKED.equals(item.getStatus())) {
                blocked++;
            } else if (AgentExecutionStatus.CASE_SKIPPED.equals(item.getStatus())) {
                skipped++;
            } else {
                unexecuted++;
            }
        }
        String status;
        if (unexecuted > 0) {
            status = AgentExecutionStatus.RUNNING;
        } else if (failed > 0 || blocked > 0 || skipped > 0) {
            status = success > 0 ? AgentExecutionStatus.PARTIAL_SUCCESS : AgentExecutionStatus.FAILED;
        } else {
            status = AgentExecutionStatus.SUCCESS;
        }
        agentExecutionMapper.updateTaskCounts(taskId, status, success, failed, blocked, skipped, unexecuted,
                requireUserId(), System.currentTimeMillis());
    }

    private List<AgentExecutionCaseDTO> resolveCreateCases(String projectId, String testPlanId, List<String> caseIds) {
        List<String> orderedCaseIds;
        Map<String, String> planCaseMap = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(testPlanId)) {
            TestPlanFunctionalCaseExample example = new TestPlanFunctionalCaseExample();
            example.createCriteria().andTestPlanIdEqualTo(testPlanId);
            example.setOrderByClause("pos ASC, create_time ASC");
            List<TestPlanFunctionalCase> planCases = testPlanFunctionalCaseMapper.selectByExample(example);
            if (CollectionUtils.isEmpty(planCases)) {
                return List.of();
            }
            Set<String> selected = CollectionUtils.isEmpty(caseIds) ? null : new LinkedHashSet<>(caseIds);
            orderedCaseIds = new ArrayList<>();
            for (TestPlanFunctionalCase planCase : planCases) {
                if (selected == null || selected.contains(planCase.getFunctionalCaseId())) {
                    orderedCaseIds.add(planCase.getFunctionalCaseId());
                    planCaseMap.put(planCase.getFunctionalCaseId(), planCase.getId());
                }
            }
        } else {
            if (CollectionUtils.isEmpty(caseIds)) {
                throw new MSException("计划外执行必须提供 caseIds");
            }
            orderedCaseIds = new ArrayList<>(new LinkedHashSet<>(caseIds));
        }
        return validateAndMapExecutionCases(projectId, orderedCaseIds, testPlanId, planCaseMap);
    }

    private List<AgentExecutionCaseDTO> validateAndMapExecutionCases(String projectId, List<String> caseIds,
                                                                     String testPlanId, Map<String, String> planCaseMap) {
        List<AgentExecutionCaseDTO> list = new ArrayList<>();
        for (String caseId : caseIds) {
            FunctionalCase functionalCase = functionalCaseMapper.selectByPrimaryKey(caseId);
            if (functionalCase == null) {
                throw new MSException("用例不存在：" + caseId);
            }
            if (!StringUtils.equals(functionalCase.getProjectId(), projectId)) {
                throw new MSException("用例不属于当前项目：" + caseId);
            }
            if (BooleanUtils.isTrue(functionalCase.getDeleted()) || !BooleanUtils.isTrue(functionalCase.getLatest())) {
                throw new MSException("用例已删除或不是最新版本：" + caseId);
            }
            AgentExecutionCaseDTO item = new AgentExecutionCaseDTO();
            item.setCaseId(functionalCase.getId());
            item.setCaseNum(functionalCase.getNum());
            item.setCaseName(functionalCase.getName());
            item.setTestPlanId(testPlanId);
            item.setTestPlanCaseId(planCaseMap.get(functionalCase.getId()));
            list.add(item);
        }
        return list;
    }

    private List<AgentCaseDTO> validateAndMapCases(String projectId, List<String> caseIds, String testPlanId) {
        Map<String, String> planCaseMap = StringUtils.isBlank(testPlanId) ? Map.of() : buildPlanCaseMap(testPlanId);
        return validateAndMapExecutionCases(projectId, new ArrayList<>(new LinkedHashSet<>(caseIds)), testPlanId, planCaseMap)
                .stream()
                .map(this::toAgentCase)
                .toList();
    }

    private Map<String, String> buildPlanCaseMap(String testPlanId) {
        TestPlanFunctionalCaseExample example = new TestPlanFunctionalCaseExample();
        example.createCriteria().andTestPlanIdEqualTo(testPlanId);
        return testPlanFunctionalCaseMapper.selectByExample(example).stream()
                .collect(Collectors.toMap(TestPlanFunctionalCase::getFunctionalCaseId, TestPlanFunctionalCase::getId, (a, b) -> a, LinkedHashMap::new));
    }

    private List<AgentCaseDTO> searchPlanCases(String projectId, String testPlanId, int pageSize, boolean includeSteps) {
        AgentCaseSearchRequest searchRequest = new AgentCaseSearchRequest();
        searchRequest.setProjectId(projectId);
        searchRequest.setTestPlanId(testPlanId);
        searchRequest.setCurrent(1);
        searchRequest.setPageSize(Math.min(pageSize, AgentConstants.MAX_PAGE_SIZE));
        searchRequest.setIncludeSteps(includeSteps);
        AgentCaseSearchResponse response = agentFunctionalCaseSearchService.search(searchRequest);
        return response.getCases();
    }

    private void fillResolvedCases(AgentExecutionResolveResponse response, List<AgentCaseDTO> cases, String testPlanId,
                                   int threshold, String message) {
        response.setStatus(CollectionUtils.size(cases) > threshold ? AgentExecutionStatus.WAITING_CONFIRMATION : AgentExecutionStatus.CREATED);
        response.setExecutable(CollectionUtils.isNotEmpty(cases));
        response.setConfirmationRequired(CollectionUtils.size(cases) > threshold);
        response.setConfirmationReason(response.isConfirmationRequired() ? "执行范围超过 " + threshold + " 条，需要确认后继续" : null);
        response.setTestPlanId(testPlanId);
        response.setTotal(CollectionUtils.size(cases));
        response.setCases(cases);
        response.setMessage(message);
    }

    private void assertPlanBelongsToProject(String projectId, String testPlanId) {
        TestPlan plan = testPlanMapper.selectByPrimaryKey(testPlanId);
        if (plan == null) {
            throw new MSException("测试计划不存在：" + testPlanId);
        }
        if (!StringUtils.equals(plan.getProjectId(), projectId)) {
            throw new MSException("测试计划不属于当前项目：" + testPlanId);
        }
        if (StringUtils.equalsIgnoreCase(plan.getStatus(), "ARCHIVED")) {
            throw new MSException("测试计划已归档，不允许创建 AI 执行任务：" + testPlanId);
        }
    }

    private AgentExecutionTaskDTO requireTask(String id) {
        AgentExecutionTaskDTO task = agentExecutionMapper.selectTaskById(id);
        if (task == null) {
            throw new MSException("AI 执行任务不存在：" + id);
        }
        String resolvedProjectId = agentProjectService.resolveProjectId(task.getProjectId());
        if (!StringUtils.equals(resolvedProjectId, task.getProjectId())) {
            throw new MSException("AI 执行任务项目上下文校验失败：" + id);
        }
        return task;
    }

    private AgentExecutionTaskDTO hydrate(AgentExecutionTaskDTO task) {
        task.setCases(agentExecutionMapper.selectCasesByTaskId(task.getId()));
        return task;
    }

    private void assertNotTerminal(AgentExecutionTaskDTO task) {
        if (AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
            throw new MSException("终态任务不允许继续操作：" + task.getStatus());
        }
    }

    private String toCaseStatus(String result) {
        if (StringUtils.equalsIgnoreCase(result, ResultStatus.SUCCESS.name())) {
            return AgentExecutionStatus.SUCCESS;
        }
        if (StringUtils.equalsIgnoreCase(result, ResultStatus.BLOCKED.name())) {
            return AgentExecutionStatus.CASE_BLOCKED;
        }
        if (StringUtils.equalsIgnoreCase(result, ExecStatus.PENDING.name())
                || StringUtils.equalsIgnoreCase(result, ExecStatus.STOPPED.name())) {
            return AgentExecutionStatus.CASE_SKIPPED;
        }
        return AgentExecutionStatus.FAILED;
    }

    private AgentCaseDTO toAgentCase(AgentExecutionCaseDTO executionCase) {
        AgentCaseDTO dto = new AgentCaseDTO();
        dto.setCaseId(executionCase.getCaseId());
        dto.setNum(executionCase.getCaseNum());
        dto.setName(executionCase.getCaseName());
        dto.setTestPlanId(executionCase.getTestPlanId());
        dto.setTestPlanCaseId(executionCase.getTestPlanCaseId());
        dto.setLastExecuteResult(executionCase.getResult());
        return dto;
    }

    private void appendEvent(String taskId, String caseId, String level, String type, String message, Map<String, Object> metadata) {
        Long max = agentExecutionMapper.selectMaxEventSequence(taskId);
        AgentExecutionEventDTO event = new AgentExecutionEventDTO();
        event.setId(IDGenerator.nextStr());
        event.setTaskId(taskId);
        event.setCaseId(caseId);
        event.setSequence((max == null ? 0L : max) + 1);
        event.setEventTime(System.currentTimeMillis());
        event.setLevel(level);
        event.setEventType(type);
        event.setMessage(message);
        event.setSanitizedMetadata(metadata == null ? null : JSON.toJSONString(metadata));
        event.setCreateUser(requireUserId());
        agentExecutionMapper.insertEvent(event);
    }

    private int normalizeThreshold(Integer threshold) {
        if (threshold == null || threshold < 1) {
            return DEFAULT_CONFIRM_THRESHOLD;
        }
        return Math.min(threshold, AgentConstants.MAX_PAGE_SIZE);
    }

    private String requireUserId() {
        String userId = SessionUtils.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new MSException("无法解析当前用户");
        }
        return userId;
    }
}
