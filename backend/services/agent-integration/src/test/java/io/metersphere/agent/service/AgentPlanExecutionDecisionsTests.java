package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionStepResultDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentPlanExecutionDecisionsTests {
    private final AgentTaskClaimService tasks = mock(AgentTaskClaimService.class);
    private final AgentTestPlanQueryService plans = mock(AgentTestPlanQueryService.class);
    private final AgentTaskExecutionApplicationService execution = mock(AgentTaskExecutionApplicationService.class);
    private final AgentExecutionArtifactService artifacts = mock(AgentExecutionArtifactService.class);
    private final AgentPlanExecutionDecisions decisions = new AgentPlanExecutionDecisions(tasks, plans, execution, artifacts);
    private final AgentExecutionTaskDTO task = new AgentExecutionTaskDTO();

    @BeforeEach
    void setup() {
        var token = new AgentToken();
        token.setScopes("[\"" + AgentTokenScope.TASK_READ + "\"]");
        AgentTokenContext.set(token);
        task.setId("task");
        task.setStatus("RUNNING");
        task.setCurrentExecutionId("run");
        task.setTestPlanId("plan");
        when(tasks.getPersonalTask("task")).thenReturn(task);
        when(plans.accountedPlanCases(task)).thenReturn(List.of());
    }

    @AfterEach
    void clearToken() {
        AgentTokenContext.clear();
    }

    @Test
    void nativeBatchChoosesAnotherCaseWhileAnEarlierCaseIsWaiting() {
        task.setCases(List.of(caseEntry("waiting"), caseEntry("available")));
        when(plans.readFrozenExecutionContract(task, "waiting")).thenReturn(contract("waiting",
                Map.of("step", "WAITING_EVIDENCE"), List.of()));
        when(plans.readFrozenExecutionContract(task, "available")).thenReturn(contract("available", Map.of(), List.of()));
        var next = decisions.next("task", "run");
        assertEquals("CONTINUE_CASE", next.get("action"));
        assertEquals("available", next.get("testPlanCaseId"));
        assertEquals("WAITING_EVIDENCE", ((Map<?, ?>) ((List<?>) next.get("pending")).getFirst()).get("reason"));
        assertEquals("RETRY_EVIDENCE_UPLOAD", decisions.decide(Map.of("taskId", "task", "executionId", "run",
                "testPlanCaseId", "waiting", "observation", Map.of("kind", "RESUMING"))).get("action"));
    }

    @Test
    void nativeSubmitPersistsFailureAndReadsBackExactEvidenceBeforeAdvancing() {
        task.setCases(List.of(caseEntry("available")));
        when(plans.readFrozenExecutionContract(task, "available")).thenReturn(
                contract("available", Map.of(), List.of()), contract("available", Map.of(), List.of("step")));
        var artifact = new AgentExecutionArtifactDTO();
        artifact.setId("evidence");
        artifact.setCaseId("available");
        artifact.setExecutionId("run");
        artifact.setRedacted(true);
        artifact.setStatus("AVAILABLE");
        when(artifacts.list("task")).thenReturn(List.of(artifact));
        var receipt = new AgentExecutionStepResultDTO();
        receipt.setStatus("FAILED");
        receipt.setArtifactIds("[\"evidence\"]");
        when(execution.readStepResult("task", "run", "runtime", "request")).thenReturn(receipt);
        var result = decisions.submit(Map.ofEntries(Map.entry("taskId", "task"), Map.entry("executionId", "run"),
                Map.entry("testPlanCaseId", "available"), Map.entry("stepId", "step"), Map.entry("evidenceRef", "evidence"),
                Map.entry("requestId", "request"), Map.entry("leaseId", "lease"), Map.entry("leaseToken", "test-token"),
                Map.entry("status", "FAILED"), Map.entry("assertionResult", "[{\"actual\":\"unexpected\"}]")));
        assertEquals("CASE_FAILED_RECORDED", result.get("action"));
        verify(execution).submitStepResult(argThat(req -> "FAILED".equals(req.getStatus())
                && "runtime".equals(req.getStepId()) && List.of("evidence").equals(req.getArtifactIds())));
        assertEquals("REQUEST_FINALIZATION", decisions.next("task", "run").get("action"));
    }

    private AgentExecutionCaseDTO caseEntry(String id) {
        var entry = new AgentExecutionCaseDTO();
        entry.setCaseId(id);
        entry.setTestPlanCaseId(id);
        entry.setStatus("CREATED");
        return entry;
    }

    private Map<String, Object> contract(String caseId, Map<String, String> waits, List<String> failed) {
        return Map.of("contractVersion", 3, "caseId", caseId, "executionStepIds", Map.of("step", "runtime"),
                "waitingSteps", waits, "failedStepIds", failed,
                "contract", Map.of("steps", List.of(Map.of("id", "step"))));
    }
}
