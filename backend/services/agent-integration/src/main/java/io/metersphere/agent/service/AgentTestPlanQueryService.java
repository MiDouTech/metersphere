package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.dto.AgentCaseSearchRequest;
import io.metersphere.agent.dto.AgentCaseSearchResponse;
import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentTestPlanDTO;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.util.JSON;
import io.metersphere.agent.dto.AgentTestPlanSearchRequest;
import io.metersphere.agent.dto.AgentTestPlanSearchResponse;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentTestPlanQueryService {
    @Resource
    private AgentExecutionMapper agentExecutionMapper;
    @Resource
    private AgentProjectService agentProjectService;
    @Resource
    private AgentFunctionalCaseSearchService agentFunctionalCaseSearchService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    public AgentTestPlanSearchResponse search(AgentTestPlanSearchRequest request) {
        AgentTestPlanSearchRequest actual = request == null ? new AgentTestPlanSearchRequest() : request;
        String projectId = agentProjectService.resolveProjectId(actual.getProjectId());
        int page = actual.getPage() == null || actual.getPage() < 1 ? 1 : actual.getPage();
        int pageSize = actual.getPageSize() == null || actual.getPageSize() < 1 ? 20 : Math.min(actual.getPageSize(), AgentConstants.MAX_PAGE_SIZE);
        String keyword = StringUtils.trimToEmpty(actual.getKeyword());
        String likeKeyword = AgentProjectService.escapeLike(keyword.toLowerCase());
        boolean includeArchived = BooleanUtils.isTrue(actual.getIncludeArchived());

        AgentTestPlanSearchResponse response = new AgentTestPlanSearchResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        long total = agentExecutionMapper.countPlans(projectId, keyword, likeKeyword, actual.getStatus(), includeArchived);
        response.setTotal(total);
        response.setHasMore((long) page * pageSize < total);
        if (total == 0) {
            response.setItems(Collections.emptyList());
            return response;
        }
        response.setItems(agentExecutionMapper.searchPlans(projectId, keyword, likeKeyword, actual.getStatus(),
                includeArchived, (page - 1) * pageSize, pageSize));
        return response;
    }

    public AgentCaseSearchResponse cases(String projectId, String testPlanId, int current, int pageSize, boolean includeSteps) {
        return cases(projectId, testPlanId, current, pageSize, includeSteps, false);
    }

    public AgentCaseSearchResponse cases(String projectId, String testPlanId, int current, int pageSize,
                                        boolean includeSteps, boolean includeContractVersion) {
        AgentCaseSearchRequest request = new AgentCaseSearchRequest();
        request.setProjectId(projectId);
        request.setTestPlanId(testPlanId);
        request.setCurrent(current < 1 ? 1 : current);
        request.setPageSize(Math.min(Math.max(pageSize, 1), AgentConstants.MAX_PAGE_SIZE));
        request.setIncludeSteps(includeSteps);
        AgentCaseSearchResponse response = agentFunctionalCaseSearchService.search(request);
        if (includeContractVersion) {
            attachContractVersions(response);
        }
        return response;
    }

    private void attachContractVersions(AgentCaseSearchResponse response) {
        List<String> caseIds = response.getCases().stream()
                .map(AgentCaseDTO::getCaseId).distinct().toList();
        if (caseIds.isEmpty()) {
            return;
        }
        record CaseVersion(String caseId, String sourceVersion) {}
        Map<CaseVersion, Long> versions = new HashMap<>();
        new NamedParameterJdbcTemplate(jdbcTemplate).query("""
                SELECT case_id, source_version, MAX(version) AS contract_version
                FROM msp_case_contract
                WHERE project_id = :projectId AND state = 'PUBLISHED' AND case_id IN (:caseIds)
                GROUP BY case_id, source_version
                """, Map.of("projectId", response.getProjectId(), "caseIds", caseIds), row -> {
            versions.put(new CaseVersion(row.getString("case_id"), row.getString("source_version")),
                    row.getLong("contract_version"));
        });
        for (AgentCaseDTO item : response.getCases()) {
            if (StringUtils.isNotBlank(item.getVersionId())) {
                item.setStructuredContractVersion(versions.get(new CaseVersion(item.getCaseId(), item.getVersionId())));
            }
        }
    }

    @Resource
    private AgentBusinessAccess access;
    @Resource
    private AgentProtocolStore contractStore;
    @Resource
    private AgentProtocolAccess protocolAccess;
    @Resource
    private AgentKnowledgeService knowledgeService;

    public Map<String, Object> readExecutionContract(String project, String planId, String relationId, Long version) {
        if (StringUtils.isAnyBlank(project, planId, relationId) || version == null || version < 1) {
            throw new MSException("AGENT_VALIDATION_ERROR");
        }
        String projectId = agentProjectService.resolveProjectId(project);
        access.project(projectId, PermissionConstants.FUNCTIONAL_CASE_READ, false, AgentTokenScope.FUNCTIONAL_READ);
        access.plan(projectId, planId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT c.case_id, c.source_version, c.content_hash, c.payload
                FROM test_plan_functional_case relation
                JOIN test_plan plan ON plan.id = relation.test_plan_id AND plan.project_id = ?
                JOIN functional_case fc ON fc.id = relation.functional_case_id AND fc.project_id = plan.project_id
                JOIN msp_case_contract c ON c.case_id = fc.id AND c.project_id = fc.project_id
                    AND c.source_version = fc.version_id AND c.state = 'PUBLISHED' AND c.version = ?
                WHERE plan.id = ? AND relation.id = ? AND fc.deleted = 0
                """, projectId, version, planId, relationId);
        if (rows.size() != 1) {
            throw AgentBusinessAccess.denied();
        }
        Map<String, Object> row = rows.getFirst();
        return Map.of("testPlanId", planId, "testPlanCaseId", relationId, "caseId", row.get("case_id"),
                "sourceVersion", row.get("source_version"), "contractVersion", version,
                "contentHash", row.get("content_hash"), "contract", contractStore.data(row));
    }

    Map<String, Long> executionContractVersions(String projectId, List<AgentExecutionCaseDTO> cases) {
        AgentCaseSearchResponse response = new AgentCaseSearchResponse();
        response.setProjectId(projectId);
        response.setCases(cases.stream().map(item -> {
            AgentCaseDTO row = new AgentCaseDTO();
            row.setCaseId(item.getCaseId());
            row.setVersionId(item.getCaseVersion());
            return row;
        }).toList());
        attachContractVersions(response);
        Map<String, Long> versions = new HashMap<>();
        for (AgentCaseDTO item : response.getCases()) {
            if (item.getStructuredContractVersion() != null) {
                versions.put(item.getCaseId(), item.getStructuredContractVersion());
            }
        }
        return versions;
    }

    Map<String, Object> publishedContractForSnapshot(String projectId, String caseId, String sourceVersion, long version) {
        var rows = jdbcTemplate.queryForList("""
                SELECT payload FROM msp_case_contract
                WHERE project_id = ? AND case_id = ? AND source_version = ? AND version = ? AND state = 'PUBLISHED'
                """, projectId, caseId, sourceVersion, version);
        if (rows.size() != 1) {
            throw new MSException("PROTOCOL_CONTRACT_UNAVAILABLE");
        }
        return contractStore.data(rows.getFirst());
    }

    public Map<String, Object> readFrozenExecutionContract(AgentExecutionTaskDTO task,
                                                            String relationId) {
        String projectId = task.getProjectId();
        access.project(projectId, PermissionConstants.FUNCTIONAL_CASE_READ, false, AgentTokenScope.FUNCTIONAL_READ);
        var token = AgentTokenContext.get();
        if (token == null || !StringUtils.equals(token.getUserId(), task.getCreateUser())
                || StringUtils.isBlank(task.getTestPlanId())) {
            throw AgentBusinessAccess.denied();
        }
        access.plan(projectId, task.getTestPlanId());
        if (StringUtils.isBlank(task.getExecutionContract())) {
            throw AgentBusinessAccess.denied();
        }
        var contract = AgentProtocolData.object(JSON.parseObject(task.getExecutionContract()));
        for (var item : AgentProtocolData.objects(contract.getOrDefault("cases", List.of()))) {
            if (!(item.get("structuredContractVersion") instanceof Number version)
                    || !relationId.equals(item.get("testPlanCaseId"))) {
                continue;
            }
            Object issue = item.get("structuredContractIssue");
            if (issue instanceof String code) {
                throw new MSException(code);
            }
            String caseId = AgentProtocolData.id(item, "caseId");
            String sourceVersion = AgentProtocolData.id(item, "structuredContractSourceVersion");
            var rows = jdbcTemplate.queryForList("""
                    SELECT c.content_hash, c.payload
                    FROM msp_case_contract c
                    JOIN functional_case fc ON fc.id = c.case_id AND fc.project_id = c.project_id AND fc.deleted = 0
                    JOIN test_plan_functional_case relation ON relation.functional_case_id = c.case_id
                    WHERE c.project_id = ? AND c.case_id = ? AND c.version = ?
                        AND c.source_version = ? AND c.state = 'PUBLISHED'
                        AND relation.test_plan_id = ? AND relation.id = ?
                    """, projectId, caseId, version.longValue(), sourceVersion, task.getTestPlanId(), relationId);
            if (rows.size() == 1) {
                var row = rows.getFirst();
                Map<String, Object> published = contractStore.data(row);
                if (StringUtils.isBlank(task.getEnvironmentId())
                        || !task.getEnvironmentId().equals(published.get("environmentId"))) {
                    throw new MSException("PROTOCOL_ENVIRONMENT_MISMATCH");
                }
                Map<String, Object> result = new LinkedHashMap<>(Map.of(
                        "testPlanId", task.getTestPlanId(), "testPlanCaseId", relationId, "caseId", caseId,
                        "sourceVersion", sourceVersion, "contractVersion", version, "contentHash", row.get("content_hash"),
                        "contract", published));
                Map<String, String> stepIds = executionStepIds(task, relationId, caseId, published);
                result.put("executionAssertions", frozenAssertionRules(item, stepIds));
                attachExecutionProgress(task, result, stepIds);
                return result;
            }
            throw AgentBusinessAccess.denied();
        }
        return readLegacyExecutionContract(task, relationId, contract);
    }

    private Map<String, Object> frozenAssertionRules(Map<String, Object> item, Map<String, String> stepIds) {
        Map<String, Object> byRuntimeStep = new HashMap<>();
        for (var step : AgentProtocolData.objects(item.getOrDefault("steps", List.of()))) {
            byRuntimeStep.put(AgentProtocolData.id(step, "stepId"), step.get("assertions"));
        }
        Map<String, Object> rulesByContractStep = new LinkedHashMap<>();
        stepIds.forEach((contractStep, runtimeStep) -> {
            var rules = byRuntimeStep.get(runtimeStep);
            if (rules != null) rulesByContractStep.put(contractStep, rules);
        });
        return rulesByContractStep;
    }

    private Map<String, Object> readLegacyExecutionContract(AgentExecutionTaskDTO task, String relationId,
                                                            Map<String, Object> frozen) {
        var planCase = task.getCases().stream()
                .filter(item -> relationId.equals(item.getTestPlanCaseId()))
                .findFirst().orElseThrow(AgentBusinessAccess::denied);
        Long relation = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM test_plan_functional_case r
                JOIN functional_case c ON c.id = r.functional_case_id AND c.project_id = ? AND c.deleted = 0
                WHERE r.id = ? AND r.test_plan_id = ? AND c.id = ?
                """, Long.class, task.getProjectId(), relationId, task.getTestPlanId(), planCase.getCaseId());
        if (!Long.valueOf(1).equals(relation)) {
            throw AgentBusinessAccess.denied();
        }
        var definition = AgentProtocolData.objects(frozen.getOrDefault("cases", List.of())).stream()
                .filter(item -> planCase.getCaseId().equals(item.get("caseId")))
                .findFirst().orElseThrow(AgentBusinessAccess::denied);
        if (definition.containsKey("structuredContractVersion")) {
            throw AgentBusinessAccess.denied();
        }
        var runtimeIds = planCase.getSteps().stream().map(AgentExecutionStepDTO::getId)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, String> steps = new LinkedHashMap<>();
        var legacy = new java.util.ArrayList<Map<String, Object>>();
        for (var step : AgentProtocolData.objects(definition.getOrDefault("steps", List.of()))) {
            String id = AgentProtocolData.id(step, "stepId");
            if (!runtimeIds.contains(id) || steps.putIfAbsent(id, id) != null) {
                throw AgentBusinessAccess.denied();
            }
            var result = new LinkedHashMap<>(step);
            result.put("id", id);
            legacy.add(result);
        }
        if (legacy.isEmpty() || !steps.keySet().equals(runtimeIds)) {
            throw new MSException("EXECUTION_CONTRACT_STEP_NOT_PLANNED");
        }
        var response = new LinkedHashMap<String, Object>(Map.of(
                "testPlanId", task.getTestPlanId(), "testPlanCaseId", relationId, "caseId", planCase.getCaseId(),
                "contractVersion", 0, "contractSource", "TASK_LEGACY_SNAPSHOT", "contract", Map.of("steps", legacy)));
        attachExecutionProgress(task, response, steps);
        return response;
    }

    private void attachExecutionProgress(AgentExecutionTaskDTO task, Map<String, Object> result,
                                         Map<String, String> stepIds) {
        result.put("executionId", task.getCurrentExecutionId());
        result.put("executionStepIds", stepIds);
        String executionId = task.getCurrentExecutionId();
        var stepFacts = StringUtils.isBlank(executionId) ? Map.<String, Map<String, Object>>of()
                : latestStepFacts(task.getId(), executionId, stepIds);
        result.put("verifiedStepIds", verifiedStepIds(stepIds, stepFacts));
        result.put("failedStepIds", stepIds.entrySet().stream().filter(step -> {
            var fact = stepFacts.get(step.getValue());
            return fact != null && "FAILED".equals(fact.get("status")) && fact.get("facts_version") != null;
        }).map(Map.Entry::getKey).toList());
        var waitingSteps = new LinkedHashMap<String, String>();
        stepIds.forEach((contractStep, runtimeStep) -> {
            var fact = stepFacts.get(runtimeStep);
            if (fact != null && "BLOCKED".equals(fact.get("status"))) {
                Object code = fact.get("error_code");
                if ("WAITING_HUMAN".equals(code) || "WAITING_DATA".equals(code) || "WAITING_EVIDENCE".equals(code)
                        || "WAITING_WRITE_RECONCILIATION".equals(code)) {
                    waitingSteps.put(contractStep, (String) code);
                }
            }
        });
        var waitingReceipts = new LinkedHashMap<String, Map<String, Object>>();
        if (StringUtils.isNotBlank(executionId) && !stepIds.isEmpty()) {
            var latest = latestStepFacts(task.getId(), null, stepIds);
            stepIds.forEach((contractStep, runtimeStep) -> {
                var fact = latest.get(runtimeStep);
                if (fact != null && "BLOCKED".equals(fact.get("status"))
                        && ("WAITING_WRITE_RECONCILIATION".equals(fact.get("error_code"))
                            || "WAITING_EVIDENCE".equals(fact.get("error_code")))
                        && !"SUCCESS".equals(stepFacts.getOrDefault(runtimeStep, Map.of()).get("status"))) {
                    waitingSteps.put(contractStep, (String) fact.get("error_code"));
                    if ("WAITING_WRITE_RECONCILIATION".equals(fact.get("error_code")) && fact.get("request_id") != null) {
                        waitingReceipts.put(contractStep, Map.of("executionId", fact.get("execution_id"),
                                "requestId", fact.get("request_id"), "executionStepId", runtimeStep,
                                "retryConsumed", AgentExecutionStepResultService.RETRY_RECEIPT.equals(fact.get("request_id"))));
                    }
                }
            });
        }
        result.put("writeRecoveryReceipts", waitingReceipts);
        result.put("waitingSteps", waitingSteps);
        if (StringUtils.isNotBlank(executionId) && !stepIds.isEmpty()) {
            var resumed = jdbcTemplate.queryForList("""
                    SELECT execution_id FROM ai_execution_checkpoint
                    WHERE task_id = ? AND status = 'RESUMED' AND execution_id <> ?
                    ORDER BY resumed_at DESC, checkpoint_version DESC LIMIT 1
                    """, String.class, task.getId(), executionId);
            if (!resumed.isEmpty()) {
                var earlier = latestStepFacts(task.getId(), resumed.getFirst(), stepIds);
                result.put("previouslyVerifiedStepIds", verifiedStepIds(stepIds, earlier));
                result.put("resumedFromExecutionId", resumed.getFirst());

            }
        }
    }

    List<Map.Entry<String, Long>> validateExecutionKnowledge(AgentExecutionTaskDTO task, String relationId,
                                                            String runtimeStepId, List<String> requested) {
        var execution = readFrozenExecutionContract(task, relationId);
        if (!AgentProtocolData.object(execution.get("executionStepIds")).containsValue(runtimeStepId))
            throw AgentBusinessAccess.denied();
        var contract = AgentProtocolData.object(execution.get("contract"));
        var eligible = new HashSet<String>();
        for (var item : AgentProtocolData.objects(contract.getOrDefault("knowledgeRefs", List.of()))) {
            eligible.add(AgentProtocolData.id(item, "knowledgeId") + ":v" + AgentProtocolData.number(item, "version"));
        }
        for (var item : AgentProtocolData.objects(contract.getOrDefault("dataRequirements", List.of()))) {
            eligible.add(AgentProtocolData.text(item, "preparationMethodRef"));
        }
        if (!eligible.containsAll(requested)) throw new MSException("STEP_KNOWLEDGE_REFERENCE_INVALID");
        var token = access.activeToken(AgentTokenScope.FUNCTIONAL_READ);
        String organization = protocolAccess.organizationForProject(task.getProjectId());
        var reader = new AgentProtocolAccess.Actor(organization, token.getUserId(), token.getId(), null, false);
        return requested.stream().map(reference -> {
            int separator = reference.lastIndexOf(":v");
            String id = reference.substring(0, separator);
            long version = Long.parseLong(reference.substring(separator + 2));
            knowledgeService.fixed(organization, id, version, reader, true);
            return Map.entry(id, version);
        }).toList();
    }

    public Map<String, Object> readDataPreparationMethod(AgentExecutionTaskDTO task, String relationId, String dataKey) {
        Map<String, Object> execution = readFrozenExecutionContract(task, relationId);
        var contract = AgentProtocolData.object(execution.get("contract"));
        var requirement = AgentProtocolData.objects(contract.getOrDefault("dataRequirements", List.of())).stream()
                .filter(item -> dataKey.equals(item.get("key"))).findFirst().orElseThrow(AgentBusinessAccess::denied);
        String reference = AgentProtocolData.text(requirement, "preparationMethodRef");
        int separator = reference.lastIndexOf(":v");
        if (separator < 1 || !reference.substring(separator + 2).matches("[1-9][0-9]{0,17}")) {
            throw new MSException("PROTOCOL_KNOWLEDGE_UNAVAILABLE");
        }
        String knowledgeId = reference.substring(0, separator);
        long version = Long.parseLong(reference.substring(separator + 2));
        var token = access.activeToken(AgentTokenScope.FUNCTIONAL_READ);
        String organization = protocolAccess.organizationForProject(task.getProjectId());
        var reader = new AgentProtocolAccess.Actor(organization, token.getUserId(), token.getId(), null, false);
        var method = knowledgeService.fixed(organization, knowledgeId, version, reader, true);
        if (!"DATA_PREPARATION".equals(method.get("type"))) {
            throw new MSException("PROTOCOL_KNOWLEDGE_UNAVAILABLE");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledgeId", knowledgeId);
        result.put("version", version);
        result.put("dataKey", dataKey);
        result.put("preparation", method.get("preparation"));
        result.put("steps", method.get("steps"));
        return result;
    }

    private Map<String, Map<String, Object>> latestStepFacts(String taskId, String executionId,
                                                             Map<String, String> stepIds) {
        if (stepIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("taskId", taskId);
        parameters.put("executionId", executionId);
        parameters.put("steps", stepIds.values());
        Map<String, Map<String, Object>> facts = new HashMap<>();
        new NamedParameterJdbcTemplate(jdbcTemplate).queryForList("""
                SELECT step_id, execution_id, request_id, status, facts_version, error_code FROM (
                    SELECT step_id, execution_id, request_id, status, facts_version, error_code,
                           ROW_NUMBER() OVER (PARTITION BY step_id ORDER BY create_time DESC, id DESC) AS recency
                    FROM ai_execution_step_result
                    WHERE task_id = :taskId AND (:executionId IS NULL OR execution_id = :executionId) AND step_id IN (:steps)
                ) recent_steps
                WHERE recency = 1
                """, parameters)
                .forEach(row -> facts.put((String) row.get("step_id"), row));
        return facts;
    }

    public List<String> accountedPlanCases(AgentExecutionTaskDTO task) {
        if (StringUtils.isAnyBlank(task.getCurrentExecutionId(), task.getTestPlanId())) return List.of();
        Map<String, String> steps = new HashMap<>();
        task.getCases().forEach(item -> item.getSteps().forEach(step -> steps.put(step.getId(), step.getId())));
        var latest = latestStepFacts(task.getId(), task.getCurrentExecutionId(), steps);
        return task.getCases().stream().filter(item -> StringUtils.isNotBlank(item.getTestPlanCaseId()))
                .filter(item -> !item.getSteps().isEmpty() && (item.getSteps().stream().anyMatch(step ->
                        hasPersistedStatus(latest.get(step.getId()), "FAILED"))
                        || item.getSteps().stream().allMatch(step ->
                        hasPersistedStatus(latest.get(step.getId()), "SUCCESS"))))
                .map(AgentExecutionCaseDTO::getTestPlanCaseId).toList();
    }

    private boolean hasPersistedStatus(Map<String, Object> fact, String status) {
        return fact != null && status.equals(fact.get("status")) && fact.get("facts_version") != null;
    }

    private List<String> verifiedStepIds(Map<String, String> stepIds, Map<String, Map<String, Object>> facts) {
        return stepIds.entrySet().stream().filter(entry -> {
            var fact = facts.get(entry.getValue());
            return fact != null && "SUCCESS".equals(fact.get("status")) && fact.get("facts_version") != null;
        }).map(Map.Entry::getKey).toList();
    }

    private Map<String, String> executionStepIds(AgentExecutionTaskDTO task, String relationId, String caseId,
                                                  Map<String, Object> published) {
        var planCase = task.getCases().stream().filter(item ->
                relationId.equals(item.getTestPlanCaseId()) && caseId.equals(item.getCaseId())).findFirst();
        if (planCase.isEmpty()) {
            return Map.of();
        }
        var steps = planCase.get().getSteps().stream().sorted(Comparator
                .comparing(AgentExecutionStepDTO::getPos).thenComparing(AgentExecutionStepDTO::getId)).toList();
        Map<String, String> result = new LinkedHashMap<>();
        for (var step : AgentProtocolData.objects(published.getOrDefault("steps", List.of()))) {
            long position = AgentProtocolData.number(step, "sourcePosition");
            if (position > 0 && position <= steps.size()) {
                result.put(AgentProtocolData.id(step, "id"), steps.get((int) position - 1).getId());
            }
        }
        return result;
    }

    public AgentTestPlanDTO get(String id) {
        AgentTestPlanSearchRequest request = new AgentTestPlanSearchRequest();
        request.setKeyword(id);
        request.setIncludeArchived(true);
        request.setPageSize(AgentConstants.MAX_PAGE_SIZE);
        return search(request).getItems().stream()
                .filter(item -> StringUtils.equals(item.getId(), id))
                .findFirst()
                .orElse(null);
    }
}
