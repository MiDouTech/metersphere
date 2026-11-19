package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionActionRequest;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentHumanCreateRequest;
import io.metersphere.agent.dto.AgentHumanRequestDTO;
import io.metersphere.agent.dto.AgentRunnerControlDTO;
import io.metersphere.agent.dto.AgentRunnerEventsRequest;
import io.metersphere.agent.dto.AgentRunnerLeaseAssignmentDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseCompleteRequest;
import io.metersphere.agent.dto.AgentRunnerPollRequest;
import io.metersphere.agent.dto.AgentRunnerTaskStateRequest;
import io.metersphere.agent.dto.AgentTaskClaimRequest;
import io.metersphere.agent.dto.AgentExecutionStepSubmitRequest;
import io.metersphere.agent.dto.AgentExecutionStepResultDTO;
import io.metersphere.agent.dto.AgentArtifactPrepareRequest;
import io.metersphere.agent.dto.AgentArtifactPrepareResponse;
import io.metersphere.agent.dto.AgentArtifactCommitRequest;
import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.dto.AgentExecutionArtifactUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Single application boundary for execution mutations. REST, Runner REST and MCP
 * adapters must delegate here instead of owning task state or lease rules.
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class AgentTaskExecutionApplicationService {
    @jakarta.annotation.Resource private AgentTestPlanQueryService testPlanQueryService;
    private final AgentTaskClaimService personalTaskService;
    private final AgentRunnerService runnerService;
    private final AgentExecutionService executionService;
    private final AgentExecutionStepResultService stepResultService;
    private final AgentExecutionArtifactService artifactService;
    @jakarta.annotation.Resource private AgentExecutionCheckpointService checkpointService;

    public AgentTaskExecutionApplicationService(AgentTaskClaimService personalTaskService,
                                                AgentRunnerService runnerService,
                                                AgentExecutionService executionService,
                                                AgentExecutionStepResultService stepResultService,
                                                AgentExecutionArtifactService artifactService) {
        this.personalTaskService = personalTaskService;
        this.runnerService = runnerService;
        this.executionService = executionService;
        this.stepResultService = stepResultService;
        this.artifactService = artifactService;
    }

    public AgentRunnerLeaseAssignmentDTO claim(AgentTaskClaimRequest request) {
        return personalTaskService.claim(request);
    }

    public AgentRunnerLeaseAssignmentDTO claim(String runnerAuthorization, AgentRunnerPollRequest request) {
        return runnerService.poll(runnerAuthorization, request);
    }

    public void heartbeatLease(String leaseId, String leaseToken) {
        personalTaskService.heartbeat(leaseId, leaseToken);
    }

    public void heartbeatLease(String runnerAuthorization, String leaseId, boolean runner) {
        runnerService.renewLease(runnerAuthorization, leaseId);
    }

    public void appendEvents(String leaseId, String leaseToken, AgentRunnerEventsRequest request) {
        personalTaskService.reportEvents(leaseId, leaseToken, request);
    }

    public void appendEvents(String runnerAuthorization, AgentRunnerEventsRequest request) {
        runnerService.reportEvents(runnerAuthorization, request);
    }

    public void submitStepResult(String leaseId, String leaseToken, AgentRunnerTaskStateRequest request) {
        personalTaskService.updateState(leaseId, leaseToken, request);
    }

    public void submitRunnerState(String runnerAuthorization, String leaseId, AgentRunnerTaskStateRequest request) {
        runnerService.updateTaskState(runnerAuthorization, leaseId, request);
    }

    @Transactional(readOnly=true)
    public AgentExecutionStepResultDTO readStepResult(String taskId,String executionId,String stepId,String requestId){
        personalTaskService.getPersonalTask(taskId);
        return stepResultService.read(taskId,executionId,stepId,requestId);
    }

    public AgentExecutionStepResultDTO submitStepResult(AgentExecutionStepSubmitRequest request) {
        if (request != null) {
            personalTaskService.assertLeaseOwner(request.getLeaseId());
        }
        return stepResultService.submit(request);
    }

    public java.util.Map<String, Object> claimWriteRetry(String taskId, String executionId, String leaseId,
            String leaseToken, String testPlanCaseId, String stepId, String waitingExecutionId,
            String waitingRequestId, String businessKeyHash, String proof) {
        personalTaskService.assertLeaseOwner(leaseId);
        var frozen = testPlanQueryService.readFrozenExecutionContract(personalTaskService.getPersonalTask(taskId), testPlanCaseId);
        var contract = AgentProtocolData.object(frozen.get("contract"));
        var steps = AgentProtocolData.objects(contract.get("steps"));
        var runtimeSteps = AgentProtocolData.object(frozen.get("executionStepIds"));
        var waitingSteps = AgentProtocolData.object(frozen.get("waitingSteps"));
        var verified = AgentProtocolData.strings(frozen.get("verifiedStepIds"));
        var pending = steps.stream().map(step -> step.get("id")).filter(id -> !verified.contains(id)).findFirst();
        if (pending.filter(stepId::equals).isEmpty() || !executionId.equals(frozen.get("executionId")) || !runtimeSteps.containsKey(stepId)
                || !"WAITING_WRITE_RECONCILIATION".equals(waitingSteps.get(stepId))
                || steps.stream().noneMatch(step -> stepId.equals(step.get("id"))
                && "SIGNED_TERMINAL_NO_EFFECT_ONCE".equals(step.get("writeRecovery")))) {
            throw new io.metersphere.sdk.exception.MSException("WRITE_RECOVERY_NOT_ALLOWED");
        }
        var request = new AgentExecutionStepSubmitRequest();
        request.setTaskId(taskId);
        request.setExecutionId(executionId);
        request.setLeaseId(leaseId);
        request.setLeaseToken(leaseToken);
        request.setStepId((String) runtimeSteps.get(stepId));
        boolean granted = stepResultService.claimWriteRetry(request, waitingExecutionId,
                waitingRequestId, businessKeyHash, proof);
        return java.util.Map.of("granted", granted);
    }

    public AgentExecutionStepResultDTO submitRunnerStepResult(String authorization, String leaseId,
                                                              AgentExecutionStepSubmitRequest request) {
        var lease = runnerService.requireActiveLease(authorization, leaseId);
        request.setTaskId(lease.getTaskId());
        request.setExecutionId(lease.getExecutionId());
        request.setLeaseId(leaseId);
        request.setLeaseToken(authorization.substring("Bearer ".length()));
        request.setAttempt(lease.getAttempt());
        return stepResultService.submit(request);
    }

    public AgentArtifactPrepareResponse prepareArtifact(AgentArtifactPrepareRequest request) {
        if (request != null) {
            personalTaskService.assertLeaseOwner(request.getLeaseId());
        }
        return artifactService.prepare(request);
    }

    public AgentExecutionArtifactDTO commitArtifact(AgentArtifactCommitRequest request) {
        if (request != null) {
            personalTaskService.assertLeaseOwner(request.getLeaseId());
        }
        return artifactService.commit(request);
    }

    public AgentExecutionArtifactUploadResponse uploadArtifact(String authorization, String leaseId,
                                                                MultipartFile file, String caseId, String stepId,
                                                                String purpose, String sha256, Boolean redacted) {
        return artifactService.upload(authorization, leaseId, file, caseId, stepId, purpose, sha256, redacted);
    }

    public AgentExecutionArtifactUploadResponse uploadPreparedArtifact(String authorization, String leaseId,
                                                                        String artifactId, String uploadToken,
                                                                        MultipartFile file) {
        return artifactService.uploadPrepared(authorization, leaseId, artifactId, uploadToken, file);
    }

    public void complete(String leaseId, String leaseToken, AgentRunnerLeaseCompleteRequest request) {
        personalTaskService.complete(leaseId, leaseToken, request);
    }

    public void completeRunnerLease(String runnerAuthorization, String leaseId, AgentRunnerLeaseCompleteRequest request) {
        runnerService.completeLease(runnerAuthorization, leaseId, request);
    }

    public void fail(String leaseId, String leaseToken, String reason) {
        AgentRunnerLeaseCompleteRequest request = new AgentRunnerLeaseCompleteRequest();
        request.setOutcome("FAILED");
        request.setReason(reason);
        complete(leaseId, leaseToken, request);
    }

    public void release(String taskId, String leaseId, String leaseToken, String reason) {
        personalTaskService.release(taskId, leaseId, leaseToken, reason);
    }

    public AgentRunnerControlDTO control(String taskId, String leaseId, String leaseToken) {
        return personalTaskService.control(taskId, leaseId, leaseToken);
    }

    public AgentRunnerControlDTO control(String runnerAuthorization, String leaseId) {
        return runnerService.control(runnerAuthorization, leaseId);
    }

    public AgentHumanRequestDTO createHumanRequest(String taskId, String leaseId, String leaseToken,
                                                    AgentHumanCreateRequest request) {
        return personalTaskService.requestHuman(taskId, leaseId, leaseToken, request);
    }

    public io.metersphere.agent.dto.AgentExecutionCheckpointDTO createCheckpoint(String taskId,String leaseId,String leaseToken,
                                                                                   io.metersphere.agent.dto.AgentCheckpointCreateRequest request){
        personalTaskService.control(taskId,leaseId,leaseToken);
        return checkpointService.create(taskId,leaseId,request);
    }

    public AgentExecutionTaskDTO cancel(String taskId, AgentExecutionActionRequest request) {
        return executionService.cancel(taskId, request == null ? null : request.getReason());
    }
}
