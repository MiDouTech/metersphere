package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.quality.GlobalQualityGate;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentStepResultFactsTests {
    private org.mockito.MockedStatic<io.metersphere.system.uid.IDGenerator> ids;
    @org.junit.jupiter.api.BeforeEach void ids(){ids=mockStatic(io.metersphere.system.uid.IDGenerator.class);ids.when(io.metersphere.system.uid.IDGenerator::nextStr).thenAnswer(call->java.util.UUID.randomUUID().toString());}
    @org.junit.jupiter.api.AfterEach void closeIds(){ids.close();}

    @Test void recoveryProofIsBoundToLeaseAndWaitingReceiptAndCanOnlyBeConsumedOnce() throws Exception {
        var mapper = mock(AgentExecutionMapper.class);
        var runner = mock(AgentRunnerService.class);
        var gate = mock(GlobalQualityGate.class);
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var service = new AgentExecutionStepResultService(mapper, runner);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "finalization", mock(AgentExecutionFinalizationService.class));
        ReflectionTestUtils.setField(service, "factCipher", new io.metersphere.agent.security.AgentFactCipher());
        ReflectionTestUtils.setField(service, "qualityGate", gate);
        var pair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        ReflectionTestUtils.setField(service, "writeRecoveryPublicKey",
                java.util.Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        var lease = new AgentRunnerLeaseDTO();
        lease.setTaskId("task");
        lease.setExecutionId("current");
        lease.setId("lease");
        lease.setAttempt(2);
        when(runner.requireActiveLease("Bearer token", "lease")).thenReturn(lease);
        var task = new AgentExecutionTaskDTO();
        task.setId("task");
        task.setRunnerLeaseId("lease");
        var step = new AgentExecutionStepDTO();
        step.setId("step");
        when(mapper.selectTaskById("task")).thenReturn(task);
        when(mapper.selectStepsByTaskId("task")).thenReturn(List.of(step));
        when(gate.binding("current", "task")).thenReturn(new GlobalQualityGate.Binding("policy", 1, "hash", "{}"));

        var request = new AgentExecutionStepSubmitRequest();
        request.setTaskId("task");
        request.setExecutionId("current");
        request.setLeaseId("lease");
        request.setLeaseToken("token");
        request.setStepId("step");
        String keyHash = "a".repeat(64);
        String expires = Long.toString(System.currentTimeMillis() + 60_000);
        var signer = java.security.Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(io.metersphere.sdk.util.JSON.toJSONString(List.of(
                "MSP_TERMINAL_NO_EFFECT_AND_CONDITION_CHANGED_V1", "task", "current", "lease",
                "step", "prior", "waiting", keyHash, expires)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String proof = expires + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        assertEquals("WRITE_RECOVERY_PROOF_INVALID", assertThrows(MSException.class,
                () -> service.claimWriteRetry(request, "prior", "other", keyHash, proof)).getMessage());
        assertEquals("WRITE_RECOVERY_PROOF_INVALID", assertThrows(MSException.class,
                () -> service.claimWriteRetry(request, "prior", "waiting", "b".repeat(64), proof)).getMessage());
        var waiting = new AgentExecutionStepResultDTO();
        waiting.setExecutionId("prior");
        waiting.setRequestId("waiting");
        waiting.setStatus("BLOCKED");
        waiting.setErrorCode("WAITING_WRITE_RECONCILIATION");
        waiting.setInputSnapshot(keyHash);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("task"), eq("step"),
                eq(AgentExecutionStepResultService.RETRY_RECEIPT))).thenReturn(List.of(), List.of("used"));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<AgentExecutionStepResultDTO>>any(),
                eq("task"), eq("step"))).thenReturn(List.of(waiting));
        assertTrue(service.claimWriteRetry(request, "prior", "waiting", keyHash, proof));
        assertFalse(service.claimWriteRetry(request, "prior", "waiting", keyHash, proof));
        verify(mapper, times(1)).insertStepResult(argThat(saved ->
                AgentExecutionStepResultService.RETRY_RECEIPT.equals(saved.getRequestId())));
        assertEquals("STEP_RESULT_REQUEST_INVALID", assertThrows(MSException.class,
                () -> service.submit(request)).getMessage());
    }

    @Test void savedFactsMatchEvaluatedFactsAndChangedReplayIsRejected(){
        var mapper=mock(AgentExecutionMapper.class);var runner=mock(AgentRunnerService.class);var gate=mock(GlobalQualityGate.class);var audit=mock(AgentQualityAuditService.class);
        var service=new AgentExecutionStepResultService(mapper,runner);
        var cipher=mock(io.metersphere.agent.security.AgentFactCipher.class);when(cipher.protect(any())).thenAnswer(call->call.getArgument(0));ReflectionTestUtils.setField(service,"factCipher",cipher);ReflectionTestUtils.setField(service,"qualityGate",gate);ReflectionTestUtils.setField(service,"qualityAudit",audit);ReflectionTestUtils.setField(service,"finalization",mock(AgentExecutionFinalizationService.class));
        var lease=new AgentRunnerLeaseDTO();lease.setId("l1");lease.setTaskId("t1");lease.setExecutionId("e1");lease.setAttempt(1);
        when(runner.requireActiveLease("Bearer token","l1")).thenReturn(lease);
        var task=new AgentExecutionTaskDTO();task.setId("t1");task.setRunnerLeaseId("l1");task.setExecutionContractHash("contract");when(mapper.selectTaskById("t1")).thenReturn(task);
        var step=new AgentExecutionStepDTO();step.setId("s1");when(mapper.selectStepsByTaskId("t1")).thenReturn(List.of(step));
        when(gate.binding("e1","t1")).thenReturn(new GlobalQualityGate.Binding("policy",1,"hash","{}"));
        var r=new AgentExecutionStepSubmitRequest();r.setTaskId("t1");r.setExecutionId("e1");r.setLeaseId("l1");r.setLeaseToken("token");r.setStepId("s1");r.setStatus("SUCCESS");r.setRequestId("req");r.setAssertionResult("[{\"actual\":\"token=literal\"}]");
        var planQuery = mock(AgentTestPlanQueryService.class);
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "testPlanQueryService", planQuery);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        r.setTestPlanCaseId("relation");
        r.setUsedKnowledgeRefs(List.of("used:v2", "used:v2"));
        when(planQuery.validateExecutionKnowledge(task, "relation", "s1", List.of("used:v2")))
                .thenReturn(List.of(java.util.Map.entry("used", 2L)));
        var saved=service.submit(r);assertEquals(r.getAssertionResult(),saved.getAssertionResult());
        verify(jdbc).update(contains("INSERT INTO ai_execution_knowledge_usage"), eq(saved.getId()), eq("used"), eq(2L));
        verify(gate).verifySuccess(task,"e1",step,saved.getAssertionResult(),List.of());
        when(mapper.selectStepResultByRequest("e1","s1","req")).thenReturn(saved);
        assertEquals(saved.getId(),service.submit(r).getId());verify(mapper,times(1)).insertStepResult(any());
        r.setAssertionResult("[{\"actual\":\"different\"}]");
        assertEquals("STEP_RESULT_REQUEST_CONFLICT",assertThrows(MSException.class,()->service.submit(r)).getMessage());
        verify(audit).rejected(eq(r),eq("STEP_RESULT_REQUEST_CONFLICT"),anyString(),argThat(hashes -> hashes.get("payload") != null));
    }
}
