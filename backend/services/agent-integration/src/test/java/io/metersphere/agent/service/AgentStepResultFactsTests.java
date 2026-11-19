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
        var saved=service.submit(r);assertEquals(r.getAssertionResult(),saved.getAssertionResult());
        verify(gate).verifySuccess(task,"e1",step,saved.getAssertionResult(),List.of());
        when(mapper.selectStepResultByRequest("e1","s1","req")).thenReturn(saved);
        assertEquals(saved.getId(),service.submit(r).getId());verify(mapper,times(1)).insertStepResult(any());
        r.setAssertionResult("[{\"actual\":\"different\"}]");
        assertEquals("STEP_RESULT_REQUEST_CONFLICT",assertThrows(MSException.class,()->service.submit(r)).getMessage());
        verify(audit).rejected(eq(r),eq("STEP_RESULT_REQUEST_CONFLICT"),anyString(),argThat(hashes -> hashes.get("payload") != null));
    }
}
