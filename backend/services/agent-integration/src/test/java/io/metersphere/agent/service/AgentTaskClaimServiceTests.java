package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentRunnerLeaseDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseCompleteRequest;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class AgentTaskClaimServiceTests {
    @org.mockito.Mock private AgentExecutionClaimSafetyService claimSafety;
    @AfterEach void clear(){AgentTokenContext.clear();}
    @Test void leaseMutationRequiresSamePersonalAgentToken(){
        AgentTaskClaimService service=new AgentTaskClaimService();AgentExecutionMapper mapper=Mockito.mock(AgentExecutionMapper.class);ReflectionTestUtils.setField(service,"executionMapper",mapper);
        AgentToken token=new AgentToken();token.setId("token-a");AgentTokenContext.set(token);
        AgentRunnerLeaseDTO lease=new AgentRunnerLeaseDTO();lease.setId("l1");lease.setExecutorType("AGENT");lease.setExecutorId("token-b");Mockito.when(mapper.selectLeaseById("l1")).thenReturn(lease);
        MSException error=Assertions.assertThrows(MSException.class,()->service.assertLeaseOwner("l1"));
        Assertions.assertEquals("AGENT_TASK_LEASE_FORBIDDEN",error.getMessage());
    }
    @Test
    void batchFinalizationRejectsTaskOrExecutionThatDoesNotOwnTheLease() {
        var service = new AgentTaskClaimService();
        var mapper = Mockito.mock(AgentExecutionMapper.class);
        var runner = Mockito.mock(AgentRunnerService.class);
        ReflectionTestUtils.setField(service, "executionMapper", mapper);
        ReflectionTestUtils.setField(service, "runnerService", runner);
        var token = new AgentToken();
        token.setId("token-a");
        AgentTokenContext.set(token);
        var lease = new AgentRunnerLeaseDTO();
        lease.setTaskId("task-a");
        lease.setExecutionId("run-a");
        lease.setExecutorType("AGENT");
        lease.setExecutorId("token-a");
        Mockito.when(mapper.selectLeaseById("lease")).thenReturn(lease);
        var request = new AgentRunnerLeaseCompleteRequest();
        request.setOutcome("COMPLETED");
        request.setTaskId("task-b");
        request.setExecutionId("run-a");
        Assertions.assertThrows(MSException.class, () -> service.complete("lease", "proof", request));
        request.setTaskId("task-a");
        request.setExecutionId("run-b");
        Assertions.assertThrows(MSException.class, () -> service.complete("lease", "proof", request));
        Mockito.verifyNoInteractions(runner);

        request.setExecutionId("run-a");
        service.complete("lease", "proof", request);
        Mockito.verify(runner).completeLease("Bearer proof", "lease", request);
    }
}
