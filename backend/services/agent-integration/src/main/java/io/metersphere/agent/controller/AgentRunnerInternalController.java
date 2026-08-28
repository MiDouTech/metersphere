package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentRunnerHeartbeatRequest;
import io.metersphere.agent.dto.AgentRunnerEventsRequest;
import io.metersphere.agent.dto.AgentRunnerLeaseAssignmentDTO;
import io.metersphere.agent.dto.AgentExecutionArtifactUploadResponse;
import io.metersphere.agent.dto.AgentRunnerLeaseCompleteRequest;
import io.metersphere.agent.dto.AgentRunnerPollRequest;
import io.metersphere.agent.dto.AgentRunnerTaskStateRequest;
import io.metersphere.agent.dto.AgentRunnerControlDTO;
import io.metersphere.agent.dto.AgentCredentialResolveRequest;
import io.metersphere.agent.dto.AgentCredentialResolveResponse;
import io.metersphere.agent.service.AgentCredentialReferenceService;
import io.metersphere.agent.service.AgentRunnerService;
import io.metersphere.agent.service.AgentTaskExecutionApplicationService;
import io.metersphere.agent.service.AgentTestDataLeaseService;
import io.metersphere.agent.dto.AgentTestDataAcquireRequest;
import io.metersphere.agent.dto.AgentTestDataLeaseActionRequest;
import io.metersphere.agent.dto.AgentTestDataLeaseDTO;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/internal/ai-runner/v1", "/api/internal/ai-runner/v1"})
public class AgentRunnerInternalController {
    @Resource
    private AgentRunnerService runnerService;
    @Resource
    private AgentTaskExecutionApplicationService taskExecutionService;
    @Resource
    private AgentCredentialReferenceService credentialReferenceService;
    @Resource
    private AgentTestDataLeaseService testDataLeaseService;

    @PostMapping("/heartbeat")
    public void heartbeat(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                          @RequestBody @Valid AgentRunnerHeartbeatRequest request) {
        runnerService.heartbeat(authorization, request);
    }

    @PostMapping("/lease/poll")
    public ResponseEntity<AgentRunnerLeaseAssignmentDTO> poll(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody @Valid AgentRunnerPollRequest request) {
        AgentRunnerLeaseAssignmentDTO assignment = taskExecutionService.claim(authorization, request);
        return assignment == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(assignment);
    }

    @PostMapping("/lease/{id}/heartbeat")
    public void renewLease(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                           @PathVariable String id) {
        taskExecutionService.heartbeatLease(authorization, id, true);
    }

    @GetMapping("/lease/{id}/control")
    public AgentRunnerControlDTO control(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                         @PathVariable String id) {
        return taskExecutionService.control(authorization, id);
    }

    @PostMapping("/lease/{id}/events:batch")
    public void events(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                       @PathVariable String id,
                       @RequestBody @Valid AgentRunnerEventsRequest request) {
        if (!id.equals(request.getLeaseId())) {
            throw new MSException("RUNNER_LEASE_ID_MISMATCH");
        }
        taskExecutionService.appendEvents(authorization, request);
    }

    @PostMapping("/lease/{id}/artifact")
    public AgentExecutionArtifactUploadResponse uploadArtifact(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) String stepId,
            @RequestParam String purpose,
            @RequestParam(required = false) String sha256,
            @RequestParam(defaultValue = "false") Boolean redacted) {
        return taskExecutionService.uploadArtifact(authorization, id, file, caseId, stepId, purpose, sha256, redacted);
    }

    @PostMapping("/lease/{id}/state")
    public void updateTaskState(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                @PathVariable String id,
                                @RequestBody @Valid AgentRunnerTaskStateRequest request) {
        taskExecutionService.submitRunnerState(authorization, id, request);
    }

    @PostMapping("/lease/{id}/complete")
    public void complete(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                         @PathVariable String id,
                         @RequestBody @Valid AgentRunnerLeaseCompleteRequest request) {
        taskExecutionService.completeRunnerLease(authorization, id, request);
    }

    @PostMapping("/tasks/{taskId}/credentials/{referenceId}/resolve")
    public AgentCredentialResolveResponse resolveCredential(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String taskId, @PathVariable String referenceId,
            @RequestBody @Valid AgentCredentialResolveRequest request) {
        return credentialReferenceService.resolveForRunner(authorization, taskId, referenceId, request);
    }

    @PostMapping("/tasks/{taskId}/test-data/leases")
    public AgentTestDataLeaseDTO acquireTestData(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String taskId,@RequestBody @Valid AgentTestDataAcquireRequest request){
        var lease=runnerService.requireActiveLease(authorization,request.getLeaseId());
        if(!taskId.equals(lease.getTaskId()))throw new MSException("RUNNER_LEASE_TASK_MISMATCH");
        return testDataLeaseService.acquire(taskId,request.getDatasetId(),request.getDataKey(),request.getTtlMs());
    }

    @PostMapping("/test-data/leases/{id}/heartbeat")
    public AgentTestDataLeaseDTO heartbeatTestData(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String id,@RequestParam String runnerLeaseId,@RequestBody @Valid AgentTestDataLeaseActionRequest request){
        var runnerLease=runnerService.requireActiveLease(authorization,runnerLeaseId);
        testDataLeaseService.assertExecution(id,runnerLease.getExecutionId());
        return testDataLeaseService.heartbeat(id,request.getLeaseToken(),request.getTtlMs()==null?60000:request.getTtlMs());
    }

    @PostMapping("/test-data/leases/{id}/release")
    public void releaseTestData(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,@PathVariable String id,
            @RequestParam String runnerLeaseId,@RequestBody @Valid AgentTestDataLeaseActionRequest request){
        var runnerLease=runnerService.requireActiveLease(authorization,runnerLeaseId);
        testDataLeaseService.assertExecution(id,runnerLease.getExecutionId());
        testDataLeaseService.release(id,request.getLeaseToken());
    }

    @GetMapping("/test-data/leases/{id}/content")
    public ResponseEntity<byte[]> testDataContent(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Test-Data-Lease-Token") String dataLeaseToken,@PathVariable String id,
            @RequestParam String runnerLeaseId){
        var runnerLease=runnerService.requireActiveLease(authorization,runnerLeaseId);
        testDataLeaseService.assertExecution(id,runnerLease.getExecutionId());
        return testDataLeaseService.content(id,dataLeaseToken,runnerLease.getExecutionId());
    }
}
