package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.agent.constants.AgentExecutionMode;
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
import io.metersphere.agent.dto.AgentExecutionTaskSearchRequest;
import io.metersphere.agent.dto.AgentExecutionTaskSearchResponse;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentTestPlanDTO;
import io.metersphere.agent.dto.AgentTestPlanSearchRequest;
import io.metersphere.agent.dto.AgentTestPlanSearchResponse;
import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.resolver.AgentExecutionNaturalLanguageResolver;
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
import io.metersphere.sdk.constants.TestPlanConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.dto.request.ai.AiAgentGatewayCapabilityDTO;
import io.metersphere.system.service.ai.AiGovernanceService;
import io.metersphere.system.service.ai.provider.AiAgentGatewayService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentExecutionService {
    private static final int DEFAULT_CONFIRM_THRESHOLD = 20;
    private static final int EVENT_LIMIT_MAX = 500;
    private static final int ESTIMATE_MINUTES_PER_CASE = 2;
    private static final List<String> EXECUTABLE_PLAN_STATUSES = List.of(
            TestPlanConstants.TEST_PLAN_SHOW_STATUS_PREPARED,
            TestPlanConstants.TEST_PLAN_SHOW_STATUS_UNDERWAY,
            TestPlanConstants.TEST_PLAN_SHOW_STATUS_COMPLETED,
            "RUNNING",
            "IN_PROGRESS"
    );
    private static final List<String> HIGH_RISK_KEYWORDS = List.of(
            "删除", "支付", "发布", "权限", "批量修改", "退款", "转账", "清空",
            "delete", "payment", "pay", "publish", "privilege", "permission", "refund", "transfer"
    );

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
    @Resource
    private AgentExecLogService agentExecLogService;
    @Resource
    private AgentExecutionNaturalLanguageResolver naturalLanguageResolver;
    @Resource
    private AgentExecutionSnapshotService executionSnapshotService;
    @Resource
    private AgentExecutionContextService executionContextService;
    @Resource
    private TestAssetVersionService testAssetVersionService;
    @Resource
    private TestAssetCatalogService testAssetCatalogService;
    @Resource
    private AgentHumanRequestService humanRequestService;
    @Resource
    private AgentExecutionPlanningService executionPlanningService;
    @Resource
    private AiGovernanceService aiGovernanceService;
    @Resource
    private AiAgentGatewayService agentGatewayService;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    public List<AiAgentGatewayCapabilityDTO> executionAgents(String project) {
        String projectId = agentProjectService.resolveProjectId(project);
        return agentGatewayService.executionAgentCapabilities(projectId, requireUserId());
    }

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
            response.setSelectionMode("MANUAL");
            response.setCaseSnapshotHash(hashCaseIds(cases.stream().map(AgentCaseDTO::getCaseId).toList()));
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

        AgentExecutionNaturalLanguageResolver.Resolution naturalLanguage =
                naturalLanguageResolver.resolve(actual.getQuery(), actual.getFilters());
        if (naturalLanguage.recognized()) {
            AgentCaseSearchRequest caseSearch = new AgentCaseSearchRequest();
            caseSearch.setProjectId(projectId);
            caseSearch.setQuery(actual.getQuery());
            caseSearch.setFilters(naturalLanguage.filters());
            caseSearch.setIncludeSteps(false);
            caseSearch.setCurrent(1);
            int requestedLimit = naturalLanguage.filters().getLimit() == null
                    ? threshold + 1 : naturalLanguage.filters().getLimit();
            caseSearch.setPageSize(Math.min(Math.max(requestedLimit, 1), AgentConstants.MAX_PAGE_SIZE));
            List<AgentCaseDTO> matchedCases = new ArrayList<>(agentFunctionalCaseSearchService.search(caseSearch).getCases());
            if (Boolean.TRUE.equals(naturalLanguage.filters().getExcludeRiskActions())) {
                matchedCases.removeIf(item -> naturalLanguageResolver.containsRiskWord(item.getName()));
            }
            fillResolvedCases(response, matchedCases, null, threshold, "已按结构化自然语言条件筛选功能用例");
            response.setSelectionMode("NATURAL_LANGUAGE");
            response.setResolvedFilter(naturalLanguage.filters());
            response.setParseConfidence(naturalLanguage.confidence());
            response.setMatchedReasons(naturalLanguage.matchedReasons());
            response.setCaseSnapshotHash(hashCaseIds(matchedCases.stream().map(AgentCaseDTO::getCaseId).toList()));
            if (matchedCases.isEmpty()) {
                response.setExecutable(false);
                response.setConfirmationRequired(false);
                response.setStatus(AgentExecutionStatus.FAILED);
                response.setMessage("自然语言筛选未匹配到有效功能用例，请调整条件后重试");
            }
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
                .filter(this::isExecutablePlanStatus)
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
            response.setConfirmationReason("存在多个可执行测试计划（按进行中/最近更新排序），请确认后再创建执行任务");
            response.setMessage(response.getConfirmationReason());
            response.getWarnings().add("第一名计划不唯一时禁止静默选择，避免重复或错误执行");
            return response;
        }

        String keyword = StringUtils.defaultIfBlank(actual.getCaseKeyword(), actual.getQuery());
        long total = agentExecutionMapper.countProjectCases(projectId, StringUtils.trimToEmpty(keyword),
                AgentProjectService.escapeLike(StringUtils.trimToEmpty(keyword).toLowerCase()));
        List<AgentExecutionCaseDTO> fallbackCases = agentExecutionMapper.selectProjectCases(projectId, StringUtils.trimToEmpty(keyword),
                AgentProjectService.escapeLike(StringUtils.trimToEmpty(keyword).toLowerCase()), threshold + 1);
        List<AgentCaseDTO> mapped = fallbackCases.stream().map(this::toAgentCase).toList();
        List<String> highRiskSignals = detectHighRiskSignals(mapped.stream().map(AgentCaseDTO::getName).toList());
        response.setStatus(AgentExecutionStatus.WAITING_CONFIRMATION);
        response.setExecutable(total > 0);
        response.setConfirmationRequired(true);
        response.setConfirmationReason("未找到可自动选择的测试计划，需确认是否改为执行项目下有效功能用例");
        response.setMessage(response.getConfirmationReason());
        response.setTotal((int) total);
        response.setEstimatedMinutes(estimateMinutes((int) total));
        response.setHighRisk(!highRiskSignals.isEmpty());
        response.setHighRiskSignals(highRiskSignals);
        response.setCases(mapped);
        return response;
    }

    public AgentExecutionTaskDTO create(AgentExecutionCreateRequest request) {
        if (request == null || StringUtils.isBlank(request.getProjectId())) {
            throw new MSException("缺少 projectId");
        }
        String projectId = agentProjectService.resolveProjectId(request.getProjectId());
        String userId = requireUserId();
        String executionMode = AgentExecutionMode.normalizeMode(request.getExecutionMode());
        String dispatchMode = AgentExecutionMode.AGENT.equals(executionMode)
                ? StringUtils.defaultIfBlank(StringUtils.upperCase(request.getDispatchMode()), "PUSH") : "PULL";
        if (!List.of(AgentExecutionMode.RUNNER, AgentExecutionMode.AGENT).contains(executionMode)) {
            throw new MSException("executionMode 仅支持 RUNNER/AGENT");
        }
        String agentType = AgentExecutionMode.normalizeAgentType(request.getAgentType());
        String agentGatewayId = null;
        if (AgentExecutionMode.AGENT.equals(executionMode)) {
            if (StringUtils.isBlank(agentType) || !agentType.matches("[A-Z][A-Z0-9_-]{1,31}")) {
                throw new MSException("agentType 格式无效");
            }
            if ("PUSH".equals(dispatchMode) && !AgentExecutionMode.SUPPORTED_AGENTS.contains(agentType)) {
                throw new MSException("Agent 类型仅支持 WORKBUDDY/CURSOR/CODEX");
            }
            if ("PUSH".equals(dispatchMode)) {
                agentGatewayId = agentGatewayService.requireExecutionAgentGateway(agentType, projectId, userId);
            }
        } else {
            agentType = null;
        }
        if (StringUtils.isNotBlank(request.getIdempotencyKey())) {
            AgentExecutionTaskDTO existing = agentExecutionMapper.selectTaskByIdempotency(projectId, userId, request.getIdempotencyKey());
            if (existing != null) {
                return hydrate(existing);
            }
        }

        String targetUrl = validateTargetUrl(request.getTargetUrl());

        String testPlanId = StringUtils.trimToNull(request.getTestPlanId());
        if (StringUtils.isNotBlank(testPlanId)) {
            assertPlanBelongsToProject(projectId, testPlanId);
        }
        List<AgentExecutionCaseDTO> cases = resolveCreateCases(projectId, testPlanId, request.getCaseIds(),
                BooleanUtils.isTrue(request.getProjectWide()) && BooleanUtils.isTrue(request.getConfirmed()));
        if (CollectionUtils.isEmpty(cases)) {
            throw new MSException("未解析到可执行功能用例，任务未创建");
        }
        if (cases.size() > 100) {
            throw new MSException("单个 AI Web UI 执行任务最多允许 100 条用例");
        }
        if (AgentExecutionMode.RUNNER.equals(executionMode) && StringUtils.isBlank(request.getProviderId())) {
            throw new MSException("Runner 执行必须选择 providerId，用于将人工步骤编译为受控动作契约");
        }
        if (AgentExecutionMode.RUNNER.equals(executionMode)) {
            aiGovernanceService.assertModelAllowed(projectId, request.getProviderId());
            aiGovernanceService.assertCanStartGeneration(projectId);
        }

        List<String> highRiskSignals = detectHighRiskSignals(cases.stream().map(AgentExecutionCaseDTO::getCaseName).toList());
        List<String> confirmReasons = new ArrayList<>();
        if (cases.size() > DEFAULT_CONFIRM_THRESHOLD) {
            confirmReasons.add("执行范围超过 " + DEFAULT_CONFIRM_THRESHOLD + " 条");
        }
        if (!highRiskSignals.isEmpty()) {
            confirmReasons.add("检测到高风险关键词：" + String.join("、", highRiskSignals));
        }
        if (StringUtils.isAnyBlank(request.getEnvironmentId(), request.getTargetUrl()) && cases.size() > 1) {
            confirmReasons.add("目标环境或访问地址未明确");
        }
        boolean confirmRequired = !confirmReasons.isEmpty() && !BooleanUtils.isTrue(request.getConfirmed());
        String status = confirmRequired ? AgentExecutionStatus.WAITING_CONFIRMATION : AgentExecutionStatus.CREATED;
        Project project = projectMapper.selectByPrimaryKey(projectId);
        long now = System.currentTimeMillis();
        AgentExecutionTaskDTO task = new AgentExecutionTaskDTO();
        task.setId(IDGenerator.nextStr());
        task.setOrganizationId(project == null ? null : project.getOrganizationId());
        task.setProjectId(projectId);
        task.setTestPlanId(testPlanId);
        task.setName(StringUtils.defaultIfBlank(request.getName(),
                StringUtils.isNotBlank(testPlanId) ? "测试计划执行 - " + testPlanId : "测试资产执行 - " + cases.size() + " 个用例"));
        task.setObjective(StringUtils.defaultIfBlank(request.getObjective(), "按冻结的用例与环境上下文执行测试并回传可审计证据"));
        task.setSource(StringUtils.defaultIfBlank(request.getSource(), "API"));
        task.setSelectionMode(StringUtils.defaultIfBlank(request.getSelectionMode(),
                CollectionUtils.isNotEmpty(request.getCaseIds()) ? "MANUAL" : "NATURAL_LANGUAGE"));
        task.setPrompt(StringUtils.abbreviate(StringUtils.trimToNull(request.getPrompt()), 4000));
        task.setResolvedFilter(StringUtils.trimToNull(request.getResolvedFilter()));
        task.setCaseSnapshotHash(hashCaseIds(cases.stream().map(AgentExecutionCaseDTO::getCaseId).toList()));
        task.setPolicySnapshot(StringUtils.trimToNull(request.getPolicySnapshot()));
        task.setApprovalPolicy(StringUtils.trimToNull(request.getApprovalPolicy()));
        task.setStatus(status);
        task.setExecutionMode(executionMode);
        task.setDispatchMode(dispatchMode);
        if (!List.of("PUSH", "PULL").contains(task.getDispatchMode())) {
            throw new MSException("dispatchMode 仅支持 PUSH/PULL");
        }
        task.setAgentType(agentType);
        task.setAgentGatewayId(agentGatewayId);
        List<String> requiredCapabilities = CollectionUtils.isEmpty(request.getRequiredCapabilities())
                ? defaultRequiredCapabilities(executionMode, agentType)
                : request.getRequiredCapabilities().stream().filter(StringUtils::isNotBlank)
                .map(StringUtils::trim).distinct().sorted().toList();
        task.setRequiredCapabilities(JSON.toJSONString(requiredCapabilities));
        task.setRunnerId(request.getRunnerId());
        task.setProviderId(request.getProviderId());
        task.setEnvironmentId(request.getEnvironmentId());
        task.setTargetUrl(targetUrl);
        task.setBrowserType(request.getBrowserType());
        task.setLoginMode(request.getLoginMode());
        task.setIdempotencyKey(StringUtils.trimToNull(request.getIdempotencyKey()));
        task.setConfirmRequired(confirmRequired);
        task.setConfirmationReason(confirmRequired ? String.join("；", confirmReasons) + "，需要确认后继续" : null);
        task.setTimeoutAt(normalizeTimeout(request.getTimeoutAt(), now));
        task.setMaxAttempts(Math.min(Math.max(request.getMaxAttempts() == null ? 3 : request.getMaxAttempts(), 1), 10));
        task.setAttemptCount(0);
        task.setTotalCount(cases.size());
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setBlockedCount(0);
        task.setSkippedCount(0);
        task.setUnexecutedCount(cases.size());
        task.setWritebackStatus("PENDING");
        task.setArtifactStatus("PENDING");
        task.setExecutedBy(StringUtils.defaultIfBlank(request.getExecutedBy(), userId));
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setCreateUser(userId);
        task.setUpdateUser(userId);
        task.setVersion(0);
        agentExecutionMapper.insertTask(task);

        int pos = 0;
        List<AgentExecutionStepDTO> allStepSnapshots = new ArrayList<>();
        List<String> stableCaseAssetIds = new ArrayList<>();
        for (AgentExecutionCaseDTO item : cases) {
            FunctionalCase functionalCase = functionalCaseMapper.selectByPrimaryKey(item.getCaseId());
            if (functionalCase == null || !StringUtils.equals(projectId, functionalCase.getProjectId())) {
                throw new MSException("用例不存在或不属于当前项目：" + item.getCaseId());
            }
            String stableCaseId = StringUtils.defaultIfBlank(functionalCase.getRefId(), functionalCase.getId());
            stableCaseAssetIds.add(stableCaseId);
            item.setId(IDGenerator.nextStr());
            item.setTaskId(task.getId());
            item.setProjectId(projectId);
            item.setTestPlanId(testPlanId);
            item.setStatus(AgentExecutionStatus.CREATED);
            item.setResult(null);
            item.setPos(pos++);
            item.setRetryCount(0);
            item.setHealCount(0);
            item.setHealed(false);
            item.setWritebackStatus("PENDING");
            item.setVersion(0);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            List<AgentExecutionStepDTO> stepSnapshots = executionSnapshotService.prepareSnapshot(item, now);
            TestAssetVersionDTO assetVersion = testAssetVersionService.publish(projectId, "CASE", stableCaseId,
                    item.getCaseVersion(), item.getCaseSnapshot(), userId);
            item.setAssetVersionId(assetVersion.getId());
            if (stepSnapshots.stream().anyMatch(step -> AgentExecutionStatus.CASE_NEEDS_REVIEW.equals(step.getStatus()))) {
                throw new MSException("用例存在不可执行步骤，请先补充步骤描述和预期结果：" + item.getCaseName());
            }
            if (AgentExecutionMode.RUNNER.equals(executionMode)) {
                executionPlanningService.plan(projectId, task.getOrganizationId(), task.getProviderId(), task.getId(),
                        task.getTargetUrl(), stepSnapshots, userId);
            }
            if (stepSnapshots.stream().anyMatch(step -> "HIGH".equalsIgnoreCase(step.getRiskLevel()))) {
                throw new MSException("第一阶段禁止执行高风险 Web 动作，请拆分或调整用例后重试：" + item.getCaseName());
            }
            agentExecutionMapper.insertCase(item);
            stepSnapshots.forEach(agentExecutionMapper::insertStep);
            testAssetVersionService.relate(projectId, "EXECUTES", "TASK", task.getId(), null,
                    "CASE", stableCaseId, assetVersion.getId(),
                    JSON.toJSONString(Map.of("executionCaseId", item.getId(), "caseVersionRowId", item.getCaseId(),
                            "position", item.getPos())), userId);
            allStepSnapshots.addAll(stepSnapshots);
        }
        AgentExecutionContextService.ContextSnapshot contextSnapshot = executionContextService.build(
                task, cases, allStepSnapshots,
                testAssetCatalogService.documentContextForCases(projectId, stableCaseAssetIds));
        int contextUpdated = agentExecutionMapper.updateTaskContext(task.getId(), contextSnapshot.content(),
                contextSnapshot.sha256(), System.currentTimeMillis());
        if (contextUpdated != 1) {
            throw new MSException("执行上下文冻结失败，任务未进入调度队列");
        }
        task.setContextSnapshot(contextSnapshot.content());
        task.setContextSnapshotHash(contextSnapshot.sha256());
        if (confirmRequired) {
            humanRequestService.create(task.getId(), projectId, "APPROVAL", "确认执行范围与风险",
                    task.getConfirmationReason(), highRiskSignals.isEmpty() ? "MEDIUM" : "HIGH",
                    "system:task-create", userId, task.getTimeoutAt());
        }
        appendEvent(task.getId(), null, "INFO", "TASK_CREATED", "AI 执行任务已创建", Map.of(
                "total", cases.size(),
                "confirmRequired", confirmRequired,
                "highRisk", !highRiskSignals.isEmpty(),
                "estimatedMinutes", estimateMinutes(cases.size())
        ));
        agentExecLogService.audit("AI_EXECUTION_CREATE", task.getId(), JSON.toJSONString(Map.of(
                "projectId", projectId,
                "testPlanId", testPlanId,
                "total", cases.size(),
                "confirmRequired", confirmRequired,
                "source", task.getSource()
        )));
        if (!confirmRequired) {
            advanceAfterPrepare(task.getId());
            publishAgentDispatch(task, userId);
        }
        return get(task.getId());
    }

    private List<String> defaultRequiredCapabilities(String executionMode, String agentType) {
        if (AgentExecutionMode.RUNNER.equals(executionMode)) {
            return List.of("browser.playwright", "evidence.screenshot", "result.writeback");
        }
        String agentCapability = StringUtils.isBlank(agentType)
                ? "agent.task.execute" : "agent." + agentType.toLowerCase(Locale.ROOT);
        return List.of(agentCapability, "evidence.upload", "result.writeback");
    }

    private long normalizeTimeout(Long requestedTimeout, long now) {
        long defaultTimeout = now + 4 * 60 * 60 * 1000L;
        if (requestedTimeout == null) {
            return defaultTimeout;
        }
        long minimum = now + 60 * 1000L;
        long maximum = now + 7 * 24 * 60 * 60 * 1000L;
        return Math.min(Math.max(requestedTimeout, minimum), maximum);
    }

    public AgentExecutionTaskDTO get(String id) {
        AgentExecutionTaskDTO task = requireTask(id);
        return hydrate(task);
    }

    public AgentExecutionTaskSearchResponse searchTasks(AgentExecutionTaskSearchRequest request) {
        AgentExecutionTaskSearchRequest actual = request == null ? new AgentExecutionTaskSearchRequest() : request;
        String projectId = agentProjectService.resolveProjectId(actual.getProjectId());
        int current = Math.max(actual.getCurrent() == null ? 1 : actual.getCurrent(), 1);
        int pageSize = Math.min(Math.max(actual.getPageSize() == null ? 20 : actual.getPageSize(), 1), 100);
        String keyword = StringUtils.trimToEmpty(actual.getKeyword());
        String status = StringUtils.upperCase(StringUtils.trimToNull(actual.getStatus()));
        String verdict = StringUtils.upperCase(StringUtils.trimToNull(actual.getVerdict()));
        String mode = StringUtils.upperCase(StringUtils.trimToNull(actual.getExecutionMode()));
        AgentExecutionTaskSearchResponse response = new AgentExecutionTaskSearchResponse();
        response.setCurrent(current);
        response.setPageSize(pageSize);
        response.setTotal(agentExecutionMapper.countTasks(projectId, keyword,
                AgentProjectService.escapeLike(keyword.toLowerCase(Locale.ROOT)), status, verdict, mode));
        response.setItems(agentExecutionMapper.searchTasks(projectId, keyword,
                AgentProjectService.escapeLike(keyword.toLowerCase(Locale.ROOT)), status, verdict, mode,
                (current - 1) * pageSize, pageSize));
        return response;
    }

    public AgentExecutionEventsResponse events(String id, AgentExecutionEventsRequest request) {
        requireTask(id);
        long cursor = request == null || request.getCursor() == null ? 0L : request.getCursor();
        int limit = request == null || request.getLimit() == null ? 100 : Math.min(Math.max(request.getLimit(), 1), EVENT_LIMIT_MAX);
        List<AgentExecutionEventDTO> events = agentExecutionMapper.selectEvents(id, cursor, limit);
        events.forEach(event -> {
            if (StringUtils.isNotBlank(event.getArtifactIdsJson())) {
                event.setArtifactIds(JSON.parseArray(event.getArtifactIdsJson(), String.class));
            } else {
                event.setArtifactIds(List.of());
            }
        });
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
        AgentExecutionStateMachine.requireTransition(task.getStatus(), AgentExecutionStatus.QUEUED);
        int updated = agentExecutionMapper.confirmTask(id, task.getStatus(), normalizedVersion(task),
                AgentExecutionStatus.QUEUED, requireUserId(), System.currentTimeMillis());
        assertTransitionUpdated(updated, id);
        appendEvent(id, null, "INFO", "TASK_CONFIRMED", StringUtils.defaultIfBlank(reason, "任务范围已确认"), null);
        humanRequestService.closePending(id, "APPROVAL", "APPROVED",
                StringUtils.defaultIfBlank(reason, "执行范围已确认"), requireUserId());
        agentExecLogService.audit("AI_EXECUTION_CONFIRM", id, StringUtils.defaultIfBlank(reason, "confirmed"));
        advanceAfterPrepare(id);
        publishAgentDispatch(task, requireUserId());
        return get(id);
    }

    public AgentExecutionTaskDTO loginReady(String id, String reason) {
        AgentExecutionTaskDTO task = requireTask(id);
        assertNotTerminal(task);
        if (!List.of(AgentExecutionStatus.WAITING_LOGIN, AgentExecutionStatus.PAUSED, AgentExecutionStatus.PREPARING_BROWSER)
                .contains(task.getStatus())) {
            throw new MSException("当前状态不允许登录恢复：" + task.getStatus());
        }
        transition(task, AgentExecutionStatus.RUNNING);
        humanRequestService.closePending(id, "LOGIN", "ANSWERED",
                StringUtils.defaultIfBlank(reason, "登录已完成"), requireUserId());
        appendEvent(id, null, "INFO", "LOGIN_READY", StringUtils.defaultIfBlank(reason, "登录已恢复，可继续执行"), null);
        agentExecLogService.audit("AI_EXECUTION_LOGIN_READY", id, StringUtils.defaultIfBlank(reason, "login-ready"));
        return get(id);
    }

    public AgentExecutionTaskDTO respondHumanRequest(String taskId, String requestId,
                                                     io.metersphere.agent.dto.AgentHumanResponseRequest request) {
        AgentExecutionTaskDTO task = requireTask(taskId);
        String userId = requireUserId();
        io.metersphere.agent.dto.AgentHumanRequestDTO response =
                humanRequestService.respond(taskId, requestId, request, userId);
        if (List.of("APPROVED", "ANSWERED").contains(response.getStatus())
                && List.of(AgentExecutionStatus.WAITING_HUMAN, AgentExecutionStatus.WAITING_LOGIN)
                .contains(task.getStatus())) {
            int updated = agentExecutionMapper.transitionTaskStatus(taskId, task.getStatus(), normalizedVersion(task),
                    AgentExecutionStatus.RUNNING, userId, System.currentTimeMillis());
            assertTransitionUpdated(updated, taskId);
            appendEvent(taskId, null, "INFO", "HUMAN_REQUEST_RESOLVED",
                    StringUtils.defaultIfBlank(request.getResponse(), response.getStatus()),
                    Map.of("requestId", requestId, "action", response.getStatus()), userId);
        } else if (List.of("REJECTED", "CANCELED").contains(response.getStatus())
                && !AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
            int updated = agentExecutionMapper.finalizeHumanBlockedTask(taskId, task.getStatus(), normalizedVersion(task),
                    StringUtils.abbreviate(StringUtils.defaultIfBlank(request.getResponse(), response.getStatus()), 1000),
                    userId, System.currentTimeMillis());
            assertTransitionUpdated(updated, taskId);
            appendEvent(taskId, null, "WARN", "HUMAN_REQUEST_REJECTED",
                    StringUtils.defaultIfBlank(request.getResponse(), response.getStatus()),
                    Map.of("requestId", requestId, "action", response.getStatus()), userId);
        }
        agentExecLogService.audit("AI_EXECUTION_HUMAN_RESPONSE", taskId,
                "requestId=" + requestId + ";status=" + response.getStatus());
        return get(taskId);
    }

    public AgentExecutionTaskDTO pause(String id, String reason) {
        AgentExecutionTaskDTO task = requireTask(id);
        assertNotTerminal(task);
        if (!List.of(AgentExecutionStatus.RUNNING, AgentExecutionStatus.WAITING_LOGIN,
                AgentExecutionStatus.WAITING_HUMAN, AgentExecutionStatus.PREPARING_BROWSER)
                .contains(task.getStatus())) {
            throw new MSException("当前状态不允许暂停：" + task.getStatus());
        }
        transition(task, AgentExecutionStatus.PAUSED);
        appendEvent(id, null, "WARN", "TASK_PAUSED", StringUtils.defaultIfBlank(reason, "任务已暂停"), null);
        agentExecLogService.audit("AI_EXECUTION_PAUSE", id, StringUtils.defaultIfBlank(reason, "paused"));
        return get(id);
    }

    public AgentExecutionTaskDTO cancel(String id, String reason) {
        AgentExecutionTaskDTO task = requireTask(id);
        if (AgentExecutionStatus.CANCELED.equals(task.getStatus())) {
            return hydrate(task);
        }
        if (!AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
            transition(task, AgentExecutionStatus.CANCELED);
            appendEvent(id, null, "WARN", "TASK_CANCELED", StringUtils.defaultIfBlank(reason, "任务已取消"), null);
            agentExecLogService.audit("AI_EXECUTION_CANCEL", id, StringUtils.defaultIfBlank(reason, "canceled"));
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
        if (!AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
            throw new MSException("仅终态任务允许重试：" + task.getStatus());
        }
        long now = System.currentTimeMillis();
        agentExecutionMapper.retryFailedSteps(id, now);
        agentExecutionMapper.retryFailedCases(id, now);
        List<AgentExecutionCaseDTO> afterReset = agentExecutionMapper.selectCasesByTaskId(id);
        int success = (int) afterReset.stream().filter(item -> AgentExecutionStatus.SUCCESS.equals(item.getStatus())).count();
        int failed = (int) afterReset.stream().filter(item -> AgentExecutionStatus.FAILED.equals(item.getStatus())).count();
        int blocked = (int) afterReset.stream().filter(item -> AgentExecutionStatus.CASE_BLOCKED.equals(item.getStatus())).count();
        int skipped = (int) afterReset.stream().filter(item -> AgentExecutionStatus.CASE_SKIPPED.equals(item.getStatus())).count();
        int unexecuted = afterReset.size() - success - failed - blocked - skipped;
        int requeued = agentExecutionMapper.requeueTaskForRetry(id, task.getStatus(), normalizedVersion(task),
                requireUserId(), success, failed, blocked, skipped, unexecuted, now);
        assertTransitionUpdated(requeued, id);
        appendEvent(id, null, "INFO", "TASK_RETRY", StringUtils.defaultIfBlank(reason, "失败/阻塞用例已进入重试队列"), Map.of("retryCases", retryCases.size()));
        publishAgentDispatch(task, requireUserId());
        return get(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markAgentDispatchAccepted(String taskId, String agentType, String gatewayId) {
        AgentExecutionTaskDTO task = agentExecutionMapper.selectTaskById(taskId);
        if (task == null || !AgentExecutionMode.AGENT.equals(task.getExecutionMode())) {
            return;
        }
        if (AgentExecutionStatus.QUEUED.equals(task.getStatus())) {
            int updated = agentExecutionMapper.transitionTaskStatus(taskId, task.getStatus(), normalizedVersion(task),
                    AgentExecutionStatus.PREPARING_BROWSER, "agent:" + agentType, System.currentTimeMillis());
            assertTransitionUpdated(updated, taskId);
        }
        appendEvent(taskId, null, "INFO", "AGENT_DISPATCH_ACCEPTED",
                agentType + " Agent 已接收执行任务", Map.of("agentType", agentType, "gatewayId", gatewayId),
                "agent:" + agentType);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markAgentDispatchFailed(String taskId, String agentType, String message) {
        AgentExecutionTaskDTO task = agentExecutionMapper.selectTaskById(taskId);
        if (task == null || AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
            return;
        }
        if (AgentExecutionStateMachine.canTransition(task.getStatus(), AgentExecutionStatus.FAILED)) {
            agentExecutionMapper.transitionTaskStatus(taskId, task.getStatus(), normalizedVersion(task),
                    AgentExecutionStatus.FAILED, "agent:" + agentType, System.currentTimeMillis());
        }
        appendEvent(taskId, null, "ERROR", "AGENT_DISPATCH_FAILED",
                agentType + " Agent 调用失败：" + StringUtils.abbreviate(StringUtils.defaultString(message), 500),
                Map.of("agentType", agentType), "agent:" + agentType);
    }

    public void markCaseWritebackSuccess(String taskId, String caseId, String result) {
        if (StringUtils.isAnyBlank(taskId, caseId)) {
            return;
        }
        AgentExecutionTaskDTO task = requireTask(taskId);
        if (AgentExecutionStatus.CANCELED.equals(task.getStatus())) {
            throw new MSException("执行任务已取消，禁止继续回写结果");
        }
        if (!AgentExecutionStatus.TERMINAL.contains(task.getStatus())
                && !AgentExecutionStatus.WRITING_BACK.equals(task.getStatus())) {
            transition(task, AgentExecutionStatus.WRITING_BACK);
            appendEvent(taskId, null, "INFO", "WRITING_BACK", "开始回写执行结果", null);
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
        AgentExecutionTaskDTO task = requireTask(taskId);
        if (!AgentExecutionStatus.TERMINAL.contains(task.getStatus())
                && !AgentExecutionStatus.WRITING_BACK.equals(task.getStatus())) {
            transition(task, AgentExecutionStatus.WRITING_BACK);
        }
        agentExecutionMapper.updateCaseStatus(taskId, caseId, AgentExecutionStatus.FAILED, null,
                StringUtils.abbreviate(message, 1000), System.currentTimeMillis());
        appendEvent(taskId, caseId, "ERROR", "CASE_WRITEBACK_FAILED", StringUtils.defaultString(message, "结果回写失败"), null);
        refreshCounts(taskId);
    }

    public boolean existsWritebackIdempotency(String taskId, String caseId, String idempotencyKey) {
        if (StringUtils.isAnyBlank(taskId, caseId, idempotencyKey)) {
            return false;
        }
        return agentExecutionMapper.countWritebackIdempotency(taskId, caseId, idempotencyKey) > 0;
    }

    public void recordWritebackIdempotency(String taskId,
                                           String caseId,
                                           String idempotencyKey,
                                           String projectId,
                                           String lastExecResult) {
        if (StringUtils.isAnyBlank(taskId, caseId, idempotencyKey)) {
            return;
        }
        if (existsWritebackIdempotency(taskId, caseId, idempotencyKey)) {
            return;
        }
        agentExecutionMapper.insertWritebackIdempotency(
                IDGenerator.nextStr(),
                taskId,
                caseId,
                idempotencyKey,
                projectId,
                lastExecResult,
                requireUserId(),
                System.currentTimeMillis());
    }

    private void refreshCounts(String taskId) {
        AgentExecutionTaskDTO task = requireTask(taskId);
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
            if (AgentExecutionStatus.HOLDING.contains(task.getStatus())
                    || AgentExecutionStatus.WRITING_BACK.equals(task.getStatus())) {
                status = task.getStatus();
            } else {
                status = AgentExecutionStatus.RUNNING;
            }
        } else if (failed > 0 || blocked > 0 || skipped > 0) {
            status = success > 0 ? AgentExecutionStatus.PARTIAL_SUCCESS : AgentExecutionStatus.FAILED;
        } else {
            status = reconcileSuccessStatus(taskId, success);
        }
        agentExecutionMapper.updateTaskCounts(taskId, status, success, failed, blocked, skipped, unexecuted,
                requireUserId(), System.currentTimeMillis());
    }

    private String reconcileSuccessStatus(String taskId, int successCount) {
        int writebackEvents = agentExecutionMapper.countEventsByType(taskId, "CASE_WRITEBACK_SUCCESS");
        int evidenceEvents = agentExecutionMapper.countEvidenceEvents(taskId);
        if (writebackEvents < successCount) {
            if (agentExecutionMapper.countEventsByType(taskId, "RECONCILE_WRITEBACK") == 0) {
                appendEvent(taskId, null, "WARN", "RECONCILE_WRITEBACK",
                        "回写事件数不足，禁止标记 SUCCESS（writeback=" + writebackEvents + ", success=" + successCount + "）", null);
            }
            return AgentExecutionStatus.PARTIAL_SUCCESS;
        }
        if (evidenceEvents <= 0) {
            if (agentExecutionMapper.countEventsByType(taskId, "RECONCILE_EVIDENCE") == 0) {
                appendEvent(taskId, null, "WARN", "RECONCILE_EVIDENCE",
                        "证据未落库（截图/附件/HAR），按方案完成判定标记为 PARTIAL_SUCCESS", null);
            }
            return AgentExecutionStatus.PARTIAL_SUCCESS;
        }
        return AgentExecutionStatus.SUCCESS;
    }

    private void advanceAfterPrepare(String taskId) {
        AgentExecutionTaskDTO task = requireTask(taskId);
        if (AgentExecutionStatus.CREATED.equals(task.getStatus())) {
            transition(task, AgentExecutionStatus.QUEUED);
            appendEvent(taskId, null, "INFO", "TASK_QUEUED", "任务已进入 Runner 调度队列", null);
        }
    }

    private void publishAgentDispatch(AgentExecutionTaskDTO task, String userId) {
        if (task != null && AgentExecutionMode.AGENT.equals(task.getExecutionMode())
                && !"PULL".equals(task.getDispatchMode())) {
            eventPublisher.publishEvent(new AgentExecutionDispatchEvent(task.getId(), task.getProjectId(),
                    task.getAgentGatewayId(), task.getAgentType(), userId));
        }
    }

    private List<AgentExecutionCaseDTO> resolveCreateCases(String projectId, String testPlanId, List<String> caseIds,
                                                           boolean projectWide) {
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
        } else if (projectWide) {
            orderedCaseIds = agentExecutionMapper.selectProjectCaseIds(projectId);
            if (CollectionUtils.isEmpty(orderedCaseIds)) {
                return List.of();
            }
        } else {
            if (CollectionUtils.isEmpty(caseIds)) {
                throw new MSException("计划外执行必须提供 caseIds，或确认后使用 projectWide");
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
            item.setCaseVersion(functionalCase.getVersionId());
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
        List<String> highRiskSignals = detectHighRiskSignals(cases.stream().map(AgentCaseDTO::getName).toList());
        boolean overThreshold = CollectionUtils.size(cases) > threshold;
        boolean highRisk = !highRiskSignals.isEmpty();
        response.setStatus(overThreshold || highRisk ? AgentExecutionStatus.WAITING_CONFIRMATION : AgentExecutionStatus.CREATED);
        response.setExecutable(CollectionUtils.isNotEmpty(cases));
        response.setConfirmationRequired(overThreshold || highRisk);
        if (overThreshold && highRisk) {
            response.setConfirmationReason("执行范围超过 " + threshold + " 条，且检测到高风险关键词，需要确认后继续");
        } else if (overThreshold) {
            response.setConfirmationReason("执行范围超过 " + threshold + " 条，需要确认后继续");
        } else if (highRisk) {
            response.setConfirmationReason("检测到高风险关键词：" + String.join("、", highRiskSignals) + "，需要确认后继续");
        } else {
            response.setConfirmationReason(null);
        }
        response.setTestPlanId(testPlanId);
        response.setTotal(CollectionUtils.size(cases));
        response.setEstimatedMinutes(estimateMinutes(CollectionUtils.size(cases)));
        response.setHighRisk(highRisk);
        response.setHighRiskSignals(highRiskSignals);
        response.setCases(cases);
        response.setMessage(message);
    }

    private boolean isExecutablePlanStatus(AgentTestPlanDTO plan) {
        if (plan == null || StringUtils.isBlank(plan.getStatus())) {
            return false;
        }
        if (StringUtils.equalsIgnoreCase(plan.getStatus(), TestPlanConstants.TEST_PLAN_STATUS_ARCHIVED)) {
            return false;
        }
        return EXECUTABLE_PLAN_STATUSES.stream().anyMatch(item -> StringUtils.equalsIgnoreCase(item, plan.getStatus()))
                || StringUtils.equalsIgnoreCase(plan.getStatus(), TestPlanConstants.TEST_PLAN_STATUS_NOT_ARCHIVED);
    }

    private List<String> detectHighRiskSignals(List<String> texts) {
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        if (CollectionUtils.isEmpty(texts)) {
            return List.of();
        }
        for (String text : texts) {
            if (StringUtils.isBlank(text)) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            for (String keyword : HIGH_RISK_KEYWORDS) {
                if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                    matched.add(keyword);
                }
            }
        }
        return new ArrayList<>(matched);
    }

    private int estimateMinutes(int caseCount) {
        return Math.max(caseCount, 0) * ESTIMATE_MINUTES_PER_CASE;
    }

    private String hashCaseIds(List<String> caseIds) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = caseIds == null ? "" : caseIds.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining("\n"));
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new MSException("无法生成执行范围摘要：" + ex.getMessage());
        }
    }

    private String validateTargetUrl(String targetUrl) {
        if (StringUtils.isBlank(targetUrl)) {
            throw new MSException("AI Web UI 执行必须提供 targetUrl");
        }
        try {
            URI uri = URI.create(targetUrl.trim());
            String scheme = StringUtils.lowerCase(uri.getScheme());
            String host = StringUtils.lowerCase(StringUtils.trimToEmpty(uri.getHost()));
            if (!Set.of("http", "https").contains(scheme) || StringUtils.isBlank(host)
                    || StringUtils.isNotBlank(uri.getUserInfo())) {
                throw new MSException("targetUrl 仅允许无内嵌凭据的 HTTP(S) 地址");
            }
            if (host.equals("localhost") || host.endsWith(".localhost") || host.equals("0.0.0.0")
                    || host.equals("::1") || host.startsWith("127.") || host.equals("169.254.169.254")
                    || host.equals("metadata.google.internal")) {
                throw new MSException("targetUrl 不允许访问本机或云元数据地址");
            }
            return uri.toString();
        } catch (MSException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MSException("targetUrl 格式无效");
        }
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
        List<AgentExecutionCaseDTO> cases = agentExecutionMapper.selectCasesByTaskId(task.getId());
        Map<String, List<AgentExecutionStepDTO>> stepsByExecutionCase = agentExecutionMapper.selectStepsByTaskId(task.getId())
                .stream()
                .collect(Collectors.groupingBy(AgentExecutionStepDTO::getExecutionCaseId, LinkedHashMap::new, Collectors.toList()));
        cases.forEach(item -> item.setSteps(stepsByExecutionCase.getOrDefault(item.getId(), List.of())));
        task.setCases(cases);
        return task;
    }

    private void assertNotTerminal(AgentExecutionTaskDTO task) {
        if (AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
            throw new MSException("终态任务不允许继续操作：" + task.getStatus());
        }
    }

    private void transition(AgentExecutionTaskDTO task, String toStatus) {
        AgentExecutionStateMachine.requireTransition(task.getStatus(), toStatus);
        int updated = agentExecutionMapper.transitionTaskStatus(task.getId(), task.getStatus(), normalizedVersion(task),
                toStatus, requireUserId(), System.currentTimeMillis());
        assertTransitionUpdated(updated, task.getId());
        task.setStatus(toStatus);
        task.setVersion(normalizedVersion(task) + 1);
    }

    private int normalizedVersion(AgentExecutionTaskDTO task) {
        return task.getVersion() == null ? 0 : task.getVersion();
    }

    private void assertTransitionUpdated(int updated, String taskId) {
        if (updated != 1) {
            throw new MSException("AI 执行任务状态已变化，请刷新后重试：" + taskId);
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
        appendEvent(taskId, caseId, level, type, message, metadata, requireUserId());
    }

    private void appendEvent(String taskId, String caseId, String level, String type, String message,
                             Map<String, Object> metadata, String actor) {
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
        event.setCreateUser(actor);
        agentExecutionMapper.insertEvent(event);
    }

    private int normalizeThreshold(Integer threshold) {
        if (threshold == null || threshold < 1) {
            return DEFAULT_CONFIRM_THRESHOLD;
        }
        return Math.min(threshold, AgentConstants.MAX_PAGE_SIZE);
    }

    private String requireUserId() {
        String userId = StringUtils.defaultIfBlank(SessionUtils.getUserId(), AgentExecutionActorContext.get());
        if (StringUtils.isBlank(userId)) {
            throw new MSException("无法解析当前用户");
        }
        return userId;
    }
}
