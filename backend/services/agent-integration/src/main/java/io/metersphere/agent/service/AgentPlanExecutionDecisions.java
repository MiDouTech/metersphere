package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentExecutionStepSubmitRequest;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseCompleteRequest;
import io.metersphere.agent.security.AgentScopeAssert;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AgentPlanExecutionDecisions {
    private static final String WRITE_WAIT = "WAITING_WRITE_RECONCILIATION";
    private static final String EVIDENCE_WAIT = "WAITING_EVIDENCE";
    private static final Set<String> CASE_TERMINAL = Set.of("SUCCESS", "FAILED", "BLOCKED", "SKIPPED", "ERROR",
            "PARTIAL_SUCCESS", "CANCELED", "EXPIRED", "SKIPPED_REVIEW_REQUIRED");
    private static final Set<String> CASE_WAITING = Set.of("WAITING_HUMAN", "WAITING_LOGIN", "PAUSED", "NEEDS_REVIEW");
    private static final Set<String> CASE_ISSUES = Set.of("PROTOCOL_ENVIRONMENT_MISMATCH", "STRUCTURED_CONTRACT_STEP_MISMATCH",
            "STRUCTURED_CONTRACT_REVIEW_REQUIRED", "QUALITY_ASSERTION_INVALID", "PROTOCOL_CONTRACT_UNAVAILABLE",
            "EXECUTION_CONTRACT_STEP_NOT_PLANNED", "AGENT_RESOURCE_NOT_ACCESSIBLE");
    private final AgentTaskClaimService tasks;
    private final AgentTestPlanQueryService plans;
    private final AgentTaskExecutionApplicationService execution;
    private final AgentExecutionArtifactService artifacts;

    public AgentPlanExecutionDecisions(AgentTaskClaimService tasks, AgentTestPlanQueryService plans,
                                       AgentTaskExecutionApplicationService execution, AgentExecutionArtifactService artifacts) {
        this.tasks = tasks;
        this.plans = plans;
        this.execution = execution;
        this.artifacts = artifacts;
    }

    public Map<String, Object> decide(Map<String, Object> args) {
        var context = context(args);
        if (!context.current()) return action("STOP", "EXECUTION_NOT_CURRENT");
        var event = AgentProtocolData.object(args.get("observation"));
        String kind = field(event, "kind");
        String stepId = optional(args, "stepId");
        if (stepId != null && !context.ids().containsKey(stepId)) return context.reply(action("STOP", "STEP_NOT_IN_CONTRACT"));
        if ("READY".equals(kind) || "RESUMING".equals(kind)) return ready(context, stepId, "RESUMING".equals(kind));
        if (stepId == null) return context.reply(action("STOP", "STEP_ID_REQUIRED"));

        if ("STEP_VERIFIED".equals(kind) || "STEP_FAILED".equals(kind)) {
            boolean failed = "STEP_FAILED".equals(kind);
            if (!(failed ? context.failed() : context.verified()).contains(stepId))
                return context.reply(action("STOP", "STEP_RESULT_NOT_VERIFIED"));
            if (failed && !stepId.equals(context.pendingId()))
                return context.reply(action("STOP", "STEP_OUT_OF_ORDER"));
            if (!failed) {
                for (var step : context.steps()) {
                    if (!context.verified().contains(step.get("id"))) {
                        return context.reply(action("STOP", "STEP_RESULT_NOT_VERIFIED"));
                    }
                    if (stepId.equals(step.get("id"))) break;
                }
            }
            String evidenceId = field(event, "evidenceRef");
            if (!hasEvidence(context, evidenceId)) return context.reply(action("STOP", "EVIDENCE_NOT_AVAILABLE"));
            var receipt = execution.readStepResult(context.task().getId(), context.executionId(),
                    context.ids().get(stepId).toString(), field(event, "requestId"));
            if (!(failed ? "FAILED" : "SUCCESS").equals(receipt.getStatus()) || receipt.getArtifactIds() == null
                    || !JSON.parseArray(receipt.getArtifactIds(), String.class).contains(evidenceId))
                return context.reply(action("STOP", "STEP_RESULT_NOT_VERIFIED"));
            if (failed) return context.reply(Map.of("action", "CASE_FAILED_RECORDED", "stepId", stepId));
            return ready(context, null, false);
        }
        if (!stepId.equals(context.pendingId())) return context.reply(action("STOP", "STEP_OUT_OF_ORDER"));
        return switch (kind) {
            case "DATA_MISSING" -> dataMissing(context, event);
            case "WRITE_UNKNOWN" -> context.reply(Map.of("action", "QUERY_BUSINESS_STATE",
                    "businessKey", field(event, "businessKey")));
            case "WRITE_NOT_APPLIED" -> retry(context, event);
            default -> throw invalid();
        };
    }

    private Map<String, Object> ready(Context context, String requestedStep, boolean resume) {
        if (!context.failed().isEmpty())
            return context.reply(Map.of("action", "CASE_FAILED_RECORDED", "stepId", context.failed().getFirst()));
        String stepId = context.pendingId();
        if (stepId == null) return context.reply(Map.of("action", "SUBMIT_OBSERVATIONS"));
        if (requestedStep != null && !stepId.equals(requestedStep))
            return context.reply(action("STOP", "STEP_OUT_OF_ORDER"));
        var reason = context.waiting().get(stepId);
        if (WRITE_WAIT.equals(reason) || EVIDENCE_WAIT.equals(reason)) {
            var decision = new LinkedHashMap<>(Map.<String, Object>of("action", !resume ? "WAIT_FOR_STEP"
                    : WRITE_WAIT.equals(reason) ? "QUERY_BUSINESS_STATE" : "RETRY_EVIDENCE_UPLOAD",
                    "reason", reason, "stepId", stepId));
            if (resume && context.recoveryReceipts().containsKey(stepId))
                decision.put("recoveryReceipt", context.recoveryReceipts().get(stepId));
            return context.reply(decision);
        }
        if (resume || context.previous().contains(stepId))
            return context.reply(Map.of("action", "RECHECK_CHECKPOINT", "stepId", stepId));
        if (reason != null)
            return context.reply(Map.of("action", "WAIT_FOR_STEP", "reason", reason, "stepId", stepId));
        return context.reply(Map.of("action", "EXECUTE_STEP", "step", context.actionableStep()));
    }

    private Map<String, Object> dataMissing(Context context, Map<String, Object> event) {
        String key = field(event, "dataKey");
        if (!hasEvidence(context, field(event, "evidenceRef"))) return context.reply(action("STOP", "EVIDENCE_NOT_AVAILABLE"));
        var frozen = AgentProtocolData.object(context.frozen().get("contract"));
        var matching = AgentProtocolData.objects(frozen.getOrDefault("dataRequirements", List.of())).stream()
                .filter(item -> key.equals(item.get("key"))).findFirst();
        if (matching.isEmpty()) return context.reply(action("STOP", "DATA_RULE_MISSING"));
        var requirement = matching.get();
        var strategies = AgentProtocolData.strings(requirement.get("strategies"));
        String next = null;
        if (!flag(event, "searchExhausted") && strategies.contains("SEARCH")) next = "SEARCH_ALTERNATIVE_DATA";
        else if (!flag(event, "preparationAttempted") && strategies.contains("CREATE")) next = "PREPARE_DATA";
        if (next == null) return context.reply(Map.of("action", "WAIT_FOR_REQUIRED_DATA", "dataKey", key,
                "evidenceRef", field(event, "evidenceRef")));
        return context.reply(Map.of("action", next, "requirement", requirement,
                "method", plans.readDataPreparationMethod(context.task(), context.relationId(), key)));
    }

    private Map<String, Object> retry(Context context, Map<String, Object> event) {
        if (!hasEvidence(context, field(event, "queryEvidenceRef")))
            return context.reply(action("STOP", "EVIDENCE_NOT_AVAILABLE"));
        if (!(event.get("attempt") instanceof Number attempt) || attempt.doubleValue() != 1 || !flag(event, "conditionChanged"))
            return context.reply(action("STOP", "RETRY_CONDITIONS_NOT_MET"));
        if (event.get("recovery") == null || !WRITE_WAIT.equals(context.waiting().get(context.pendingId()))
                || !"SIGNED_TERMINAL_NO_EFFECT_ONCE".equals(context.actionableStep().get("writeRecovery")))
            return context.reply(action("STOP", "WRITE_RECONCILIATION_REQUIRED"));
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_RESULT_WRITE);
        var recovery = AgentProtocolData.object(event.get("recovery"));
        String businessKey = field(event, "businessKey");
        var claim = execution.claimWriteRetry(context.task().getId(), context.executionId(), field(recovery, "leaseId"),
                field(recovery, "leaseToken", 512), context.relationId(), context.pendingId(),
                field(recovery, "waitingExecutionId"), field(recovery, "waitingRequestId"),
                DigestUtils.sha256Hex(businessKey), field(recovery, "proof", 256));
        return Boolean.TRUE.equals(claim.get("granted"))
                ? context.reply(Map.of("action", "RETRY_WRITE_ONCE", "step", context.actionableStep(), "businessKey", businessKey))
                : context.reply(action("STOP", "WRITE_RETRY_UNAVAILABLE"));
    }

    public Map<String, Object> next(String taskId, String executionId) {
        var task = tasks.getPersonalTask(taskId);
        if (!executionId.equals(task.getCurrentExecutionId())) return action("STOP", "EXECUTION_NOT_CURRENT");
        if (CASE_TERMINAL.contains(task.getStatus())) return Map.of("action", "TASK_ALREADY_TERMINAL", "status", task.getStatus());
        if (!List.of("RUNNING", "PREPARING_BROWSER").contains(task.getStatus()))
            return Map.of("action", "WAIT_FOR_TASK", "status", task.getStatus());
        if (task.getCases().isEmpty()) return action("STOP", "EXECUTION_SCOPE_EMPTY");
        var accounted = Set.copyOf(plans.accountedPlanCases(task));
        var pending = new ArrayList<Map<String, Object>>();
        for (var item : task.getCases()) {
            String relation = item.getTestPlanCaseId();
            if (CASE_TERMINAL.contains(item.getStatus()) || (relation != null && accounted.contains(relation))) continue;
            String reason = CASE_WAITING.contains(item.getStatus()) ? item.getStatus() : "PLAN_CASE_RELATION_MISSING";
            if (relation != null && !CASE_WAITING.contains(item.getStatus())) {
                try {
                    var decision = ready(context(task, relation, executionId), null, false);
                    Object next = decision.get("action");
                    if (Set.of("SUBMIT_OBSERVATIONS", "CASE_FAILED_RECORDED").contains(next)) continue;
                    if (!Set.of("STOP", "WAIT_FOR_STEP").contains(next))
                        return Map.of("action", "CONTINUE_CASE", "caseId", item.getCaseId(), "testPlanCaseId", relation,
                                "decision", decision, "pending", pending);
                    reason = Objects.toString(decision.get("reason"), "CASE_CONTEXT_UNAVAILABLE");
                } catch (MSException failure) {
                    if (failure.getMessage() == null || !CASE_ISSUES.contains(failure.getMessage())) throw failure;
                    reason = failure.getMessage();
                }
            }
            var waiting = new LinkedHashMap<>(Map.<String, Object>of("caseId", item.getCaseId(), "reason", reason));
            if (relation != null) waiting.put("testPlanCaseId", relation);
            pending.add(waiting);
        }
        return pending.isEmpty() ? Map.of("action", "REQUEST_FINALIZATION", "taskId", taskId)
                : Map.of("action", "WAIT_FOR_CASES", "pending", pending);
    }

    public Map<String, Object> finish(Map<String, Object> args) {
        String taskId = field(args, "taskId");
        String executionId = field(args, "executionId");
        var decision = next(taskId, executionId);
        if (!"REQUEST_FINALIZATION".equals(decision.get("action"))) return decision;
        var request = new AgentRunnerLeaseCompleteRequest();
        request.setTaskId(taskId);
        request.setExecutionId(executionId);
        request.setOutcome("COMPLETED");
        execution.complete(field(args, "leaseId"), field(args, "leaseToken", 512), request);
        return Map.of("ok", true);
    }

    public Map<String, Object> submit(Map<String, Object> args) {
        var context = context(args);
        String stepId = field(args, "stepId");
        String status = Objects.toString(args.get("status"), "SUCCESS");
        if (!Set.of("SUCCESS", "FAILED").contains(status)) throw invalid();
        boolean failed = "FAILED".equals(status);
        boolean stored = (failed ? context.failed() : context.verified()).contains(stepId);
        boolean differs = (failed ? context.verified() : context.failed()).contains(stepId);
        if (!context.current() || !context.ids().containsKey(stepId) || differs || (!stored && !stepId.equals(context.pendingId())))
            return action("STOP", "EXECUTION_STEP_NOT_CURRENT");
        String evidenceId = field(args, "evidenceRef");
        if (!hasEvidence(context, evidenceId)) return action("STOP", "EVIDENCE_NOT_AVAILABLE");
        if (!stored) {
            var request = stepRequest(context, stepId, args);
            request.setStatus(status);
            request.setArtifactIds(List.of(evidenceId));
            request.setAssertionResult(field(args, "assertionResult", 65535));
            request.setTestPlanCaseId(context.relationId());
            request.setUsedKnowledgeRefs(AgentProtocolData.strings(args.getOrDefault("usedKnowledgeRefs", List.of())));
            execution.submitStepResult(request);
        }
        var follow = new LinkedHashMap<>(args);
        follow.put("observation", Map.of("kind", failed ? "STEP_FAILED" : "STEP_VERIFIED",
                "evidenceRef", evidenceId, "requestId", field(args, "requestId", 64)));
        return decide(follow);
    }

    public Object defer(Map<String, Object> args) {
        var context = context(args);
        String stepId = field(args, "stepId");
        if (!context.current() || !stepId.equals(context.pendingId())) return action("STOP", "EXECUTION_STEP_NOT_CURRENT");
        String reason = field(args, "reason");
        if (!Set.of(WRITE_WAIT, EVIDENCE_WAIT, "WAITING_HUMAN", "WAITING_DATA").contains(reason)) throw invalid();
        String evidenceId = optional(args, "evidenceRef");
        if (evidenceId == null && !Set.of(WRITE_WAIT, EVIDENCE_WAIT).contains(reason))
            return action("STOP", "EVIDENCE_REQUIRED");
        if (evidenceId != null && !hasEvidence(context, evidenceId)) return action("STOP", "EVIDENCE_NOT_AVAILABLE");
        String businessKey = optional(args, "businessKey");
        if (WRITE_WAIT.equals(reason) && businessKey == null) return action("STOP", "BUSINESS_KEY_REQUIRED");
        var request = stepRequest(context, stepId, args);
        request.setStatus("BLOCKED");
        request.setErrorCode(reason);
        request.setErrorMessage(field(args, "observedReason", 1000));
        request.setArtifactIds(evidenceId == null ? List.of() : List.of(evidenceId));
        if (WRITE_WAIT.equals(reason)) request.setInputSnapshot(DigestUtils.sha256Hex(businessKey));
        var saved = execution.submitStepResult(request);
        return WRITE_WAIT.equals(reason) ? Map.of("action", "QUERY_BUSINESS_STATE", "stepId", stepId,
                "businessKey", businessKey, "requestId", saved.getRequestId(), "executionId", context.executionId(),
                "executionStepId", context.ids().get(stepId)) : saved;
    }

    private AgentExecutionStepSubmitRequest stepRequest(Context context, String stepId, Map<String, Object> args) {
        var request = new AgentExecutionStepSubmitRequest();
        request.setTaskId(context.task().getId());
        request.setExecutionId(context.executionId());
        request.setStepId(Objects.toString(context.ids().get(stepId)));
        request.setRequestId(field(args, "requestId", 64));
        request.setLeaseId(field(args, "leaseId"));
        request.setLeaseToken(field(args, "leaseToken", 512));
        return request;
    }

    private boolean hasEvidence(Context context, String evidenceId) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_READ);
        return artifacts.list(context.task().getId()).stream().anyMatch(item -> evidenceId.equals(item.getId())
                && context.executionId().equals(item.getExecutionId()) && context.frozen().get("caseId").equals(item.getCaseId())
                && "AVAILABLE".equals(item.getStatus()) && Boolean.TRUE.equals(item.getRedacted()));
    }

    private Context context(Map<String, Object> args) {
        return context(tasks.getPersonalTask(field(args, "taskId")), field(args, "testPlanCaseId"), field(args, "executionId"));
    }

    private Context context(AgentExecutionTaskDTO task, String relationId, String executionId) {
        boolean current = executionId.equals(task.getCurrentExecutionId());
        var frozen = current ? plans.readFrozenExecutionContract(task, relationId) : Map.<String, Object>of();
        return new Context(task, relationId, executionId, current, frozen);
    }

    private record Context(AgentExecutionTaskDTO task, String relationId, String executionId, boolean current,
                           Map<String, Object> frozen) {
        Map<String, Object> ids() { return object("executionStepIds"); }
        Map<String, Object> waiting() { return object("waitingSteps"); }
        Map<String, Object> recoveryReceipts() { return object("writeRecoveryReceipts"); }
        List<String> verified() { return strings("verifiedStepIds"); }
        List<String> failed() { return strings("failedStepIds"); }
        List<String> previous() { return strings("previouslyVerifiedStepIds"); }

        Map<String, Object> actionableStep() {
            var step = pending();
            var result = new LinkedHashMap<>(step);
            Object assertions = object("executionAssertions").get(step.get("id"));
            if (assertions != null) result.put("executionAssertions", assertions);
            return result;
        }

        String pendingId() {
            return (String) pending().get("id");
        }

        List<Map<String, Object>> steps() {
            return AgentProtocolData.objects(object("contract").getOrDefault("steps", List.of()));
        }

        Map<String, Object> pending() {
            var verified = Set.copyOf(verified());
            return steps().stream().filter(step -> !verified.contains(step.get("id"))).findFirst().orElse(Map.of());
        }

        Map<String, Object> reply(Map<String, Object> decision) {
            var result = new LinkedHashMap<>(decision);
            result.put("contractVersion", frozen.get("contractVersion"));
            return result;
        }

        private Map<String, Object> object(String key) {
            return AgentProtocolData.object(frozen.getOrDefault(key, Map.of()));
        }

        private List<String> strings(String key) {
            return AgentProtocolData.strings(frozen.getOrDefault(key, List.of()));
        }
    }

    private static Map<String, Object> action(String action, String reason) {
        return Map.of("action", action, "reason", reason);
    }

    private static boolean flag(Map<String, Object> args, String key) {
        if (!(args.get(key) instanceof Boolean value)) throw invalid();
        return value;
    }

    private static String optional(Map<String, Object> args, String key) {
        return args.get(key) == null ? null : field(args, key);
    }

    private static String field(Map<String, Object> args, String key) {
        return field(args, key, 128);
    }

    private static String field(Map<String, Object> args, String key, int max) {
        if (!(args.get(key) instanceof String value) || value.isBlank() || value.length() > max) throw invalid();
        return value;
    }

    private static MSException invalid() {
        return new MSException("AGENT_VALIDATION_ERROR");
    }
}
