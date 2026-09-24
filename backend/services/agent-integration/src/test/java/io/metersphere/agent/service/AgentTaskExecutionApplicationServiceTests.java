package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentTaskExecutionApplicationServiceTests {
    @Test void runnerIdentityComesFromAuthenticatedLeaseRatherThanBody() {
        var runner=mock(AgentRunnerService.class);
        var results=mock(AgentExecutionStepResultService.class);
        var personal=mock(AgentTaskClaimService.class);
        var service=new AgentTaskExecutionApplicationService(personal,runner,mock(AgentExecutionService.class),results,mock(AgentExecutionArtifactService.class));
        var lease=new AgentRunnerLeaseDTO();lease.setTaskId("actual-task");lease.setExecutionId("actual-attempt");lease.setAttempt(2);
        when(runner.requireActiveLease("Bearer secret","lease")).thenReturn(lease);
        var request=new AgentExecutionStepSubmitRequest();request.setTaskId("other-task");request.setExecutionId("other-attempt");request.setLeaseToken("forged");
        service.submitRunnerStepResult("Bearer secret","lease",request);
        assertEquals("actual-task",request.getTaskId());assertEquals("actual-attempt",request.getExecutionId());
        assertEquals("secret",request.getLeaseToken());assertEquals(2,request.getAttempt());
        verify(results).submit(request);verifyNoInteractions(personal);
    }
    @Test void invalidLeaseCannotReachResultSubmission() {
        var runner=mock(AgentRunnerService.class);var results=mock(AgentExecutionStepResultService.class);
        var service=new AgentTaskExecutionApplicationService(mock(AgentTaskClaimService.class),runner,mock(AgentExecutionService.class),results,mock(AgentExecutionArtifactService.class));
        when(runner.requireActiveLease("Bearer wrong","lease")).thenThrow(new MSException("RUNNER_LEASE_INVALID"));
        assertThrows(MSException.class,()->service.submitRunnerStepResult("Bearer wrong","lease",new AgentExecutionStepSubmitRequest()));
        verifyNoInteractions(results);
    }
}
