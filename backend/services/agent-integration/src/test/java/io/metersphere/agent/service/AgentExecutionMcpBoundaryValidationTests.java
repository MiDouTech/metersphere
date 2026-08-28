package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentArtifactPrepareRequest;
import io.metersphere.agent.dto.AgentExecutionStepSubmitRequest;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentExecutionMcpBoundaryValidationTests {

    @Test
    void artifactPrepareRejectsMissingMcpPayloadBeforeUsingDependencies() {
        assertThrows(MSException.class, () -> new AgentExecutionArtifactService().prepare(null));
    }

    @Test
    void artifactCommitRejectsMissingMcpPayloadBeforeUsingDependencies() {
        assertThrows(MSException.class, () -> new AgentExecutionArtifactService().commit(null));
    }

    @Test
    void stepSubmitRejectsMissingMcpPayloadBeforeLeaseAuthentication() {
        AgentExecutionStepResultService service = new AgentExecutionStepResultService(
                mock(AgentExecutionMapper.class), mock(AgentRunnerService.class));
        assertThrows(MSException.class, () -> service.submit(null));
    }

    @Test
    void eventBatchRejectsMissingMcpPayloadBeforeLeaseAuthentication() {
        assertThrows(MSException.class, () -> new AgentRunnerService().reportEvents(null, null));
    }

    @Test
    void artifactPrepareChecksCurrentMcpTokenOwnsLease() {
        AgentTaskClaimService claimService = mock(AgentTaskClaimService.class);
        AgentExecutionArtifactService artifactService = mock(AgentExecutionArtifactService.class);
        AgentTaskExecutionApplicationService service = applicationService(claimService, artifactService,
                mock(AgentExecutionStepResultService.class));
        AgentArtifactPrepareRequest request = new AgentArtifactPrepareRequest();
        request.setLeaseId("lease-1");

        service.prepareArtifact(request);

        verify(claimService).assertLeaseOwner("lease-1");
        verify(artifactService).prepare(request);
    }

    @Test
    void stepSubmitChecksCurrentMcpTokenOwnsLease() {
        AgentTaskClaimService claimService = mock(AgentTaskClaimService.class);
        AgentExecutionStepResultService stepService = mock(AgentExecutionStepResultService.class);
        AgentTaskExecutionApplicationService service = applicationService(claimService,
                mock(AgentExecutionArtifactService.class), stepService);
        AgentExecutionStepSubmitRequest request = new AgentExecutionStepSubmitRequest();
        request.setLeaseId("lease-2");

        service.submitStepResult(request);

        verify(claimService).assertLeaseOwner("lease-2");
        verify(stepService).submit(request);
    }

    private AgentTaskExecutionApplicationService applicationService(AgentTaskClaimService claimService,
                                                                    AgentExecutionArtifactService artifactService,
                                                                    AgentExecutionStepResultService stepService) {
        return new AgentTaskExecutionApplicationService(claimService, mock(AgentRunnerService.class),
                mock(AgentExecutionService.class), stepService, artifactService);
    }
}
