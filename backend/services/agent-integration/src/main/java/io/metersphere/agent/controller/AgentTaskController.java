package io.metersphere.agent.controller;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentExecutionArtifactUploadResponse;
import io.metersphere.agent.dto.AgentExecutionEventsRequest;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentExecutionTaskSearchRequest;
import io.metersphere.agent.dto.AgentExecutionTaskSearchResponse;
import io.metersphere.agent.dto.AgentExecutionActionRequest;
import io.metersphere.agent.dto.AgentHumanCreateRequest;
import io.metersphere.agent.dto.AgentHumanRequestDTO;
import io.metersphere.agent.dto.AgentRunnerControlDTO;
import io.metersphere.agent.dto.AgentRunnerEventsRequest;
import io.metersphere.agent.dto.AgentRunnerLeaseAssignmentDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseCompleteRequest;
import io.metersphere.agent.dto.AgentRunnerTaskStateRequest;
import io.metersphere.agent.dto.AgentTaskClaimRequest;
import io.metersphere.agent.security.AgentScopeAssert;
import io.metersphere.agent.service.AgentExecutionService;
import io.metersphere.agent.service.AgentTaskClaimService;
import io.metersphere.agent.service.AgentTaskExecutionApplicationService;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable v1 contract used by generic pull-based Agents. */
@RestController
@RequestMapping({"/agent/v1/tasks", "/api/agent/v1/tasks"})
public class AgentTaskController {
    private static final String LEASE_TOKEN_HEADER = "X-MS-Task-Lease-Token";
    private static final String LEASE_ID_HEADER = "X-MS-Task-Lease-Id";
    private static final String ARTIFACT_UPLOAD_TOKEN_HEADER = "X-MS-Artifact-Upload-Token";

    @Resource
    private AgentExecutionService executionService;
    @Resource
    private AgentTaskClaimService claimService;
    @Resource
    private AgentTaskExecutionApplicationService taskExecutionService;

    @PostMapping("/search")
    public AgentExecutionTaskSearchResponse search(@RequestBody @Valid AgentTaskClaimRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_READ);
        return claimService.search(request);
    }

    @PostMapping("/claim")
    public ResponseEntity<AgentRunnerLeaseAssignmentDTO> claim(@RequestBody @Valid AgentTaskClaimRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_CLAIM);
        AgentRunnerLeaseAssignmentDTO assignment = taskExecutionService.claim(request);
        return assignment == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(assignment);
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<AgentRunnerLeaseAssignmentDTO> claimTask(@PathVariable String id,
                                                                   @RequestBody @Valid AgentTaskClaimRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_CLAIM);
        request.setTaskId(id);
        AgentRunnerLeaseAssignmentDTO assignment = taskExecutionService.claim(request);
        return assignment == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(assignment);
    }

    @GetMapping("/{id}")
    public AgentExecutionTaskDTO get(@PathVariable String id) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_READ);
        return claimService.getPersonalTask(id);
    }

    @GetMapping("/{id}/context")
    public Map<String, Object> context(@PathVariable String id) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_READ);
        AgentExecutionTaskDTO task = claimService.getPersonalTask(id);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("contractVersion", "v1");
        response.put("taskId", id);
        response.put("sha256", task.getContextSnapshotHash());
        response.put("snapshot", task.getContextSnapshot());
        return response;
    }

    @GetMapping("/{id}/events")
    public Object events(@PathVariable String id, AgentExecutionEventsRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_READ);
        claimService.getPersonalTask(id);
        return executionService.events(id, request);
    }

    @PostMapping("/leases/{leaseId}/heartbeat")
    public void heartbeat(@PathVariable String leaseId,
                          @RequestHeader(LEASE_TOKEN_HEADER) String leaseToken) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_CLAIM);
        taskExecutionService.heartbeatLease(leaseId, leaseToken);
    }

    @PostMapping("/leases/{leaseId}/events:batch")
    public void events(@PathVariable String leaseId,
                       @RequestHeader(LEASE_TOKEN_HEADER) String leaseToken,
                       @RequestBody @Valid AgentRunnerEventsRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_EVENT_WRITE);
        if (StringUtils.isBlank(request.getLeaseId())) {
            request.setLeaseId(leaseId);
        } else if (!StringUtils.equals(leaseId, request.getLeaseId())) {
            throw new MSException("AGENT_TASK_LEASE_ID_MISMATCH");
        }
        taskExecutionService.appendEvents(leaseId, leaseToken, request);
    }

    @PostMapping("/leases/{leaseId}/state")
    public void state(@PathVariable String leaseId,
                      @RequestHeader(LEASE_TOKEN_HEADER) String leaseToken,
                      @RequestBody @Valid AgentRunnerTaskStateRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_RESULT_WRITE);
        taskExecutionService.submitStepResult(leaseId, leaseToken, request);
    }

    @PostMapping("/leases/{leaseId}/complete")
    public void complete(@PathVariable String leaseId,
                         @RequestHeader(LEASE_TOKEN_HEADER) String leaseToken,
                         @RequestBody @Valid AgentRunnerLeaseCompleteRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_RESULT_WRITE);
        taskExecutionService.complete(leaseId, leaseToken, request);
    }

    @GetMapping("/{id}/control")
    public AgentRunnerControlDTO control(@PathVariable String id,
                                         @RequestHeader(LEASE_ID_HEADER) String leaseId,
                                         @RequestHeader(LEASE_TOKEN_HEADER) String leaseToken) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_READ);
        return taskExecutionService.control(id, leaseId, leaseToken);
    }

    @PostMapping("/{id}/human-request")
    public AgentHumanRequestDTO humanRequest(@PathVariable String id,
                                             @RequestHeader(LEASE_ID_HEADER) String leaseId,
                                             @RequestHeader(LEASE_TOKEN_HEADER) String leaseToken,
                                             @RequestBody @Valid AgentHumanCreateRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_RESULT_WRITE);
        return taskExecutionService.createHumanRequest(id, leaseId, leaseToken, request);
    }

    @PostMapping("/{id}/release")
    public void release(@PathVariable String id,
                        @RequestHeader(LEASE_ID_HEADER) String leaseId,
                        @RequestHeader(LEASE_TOKEN_HEADER) String leaseToken,
                        @RequestBody(required = false) AgentExecutionActionRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.TASK_RESULT_WRITE);
        taskExecutionService.release(id, leaseId, leaseToken, request == null ? null : request.getReason());
    }

    @PostMapping("/leases/{leaseId}/artifacts")
    public AgentExecutionArtifactUploadResponse artifact(
            @PathVariable String leaseId,
            @RequestHeader(LEASE_TOKEN_HEADER) String leaseToken,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) String stepId,
            @RequestParam String purpose,
            @RequestParam(required = false) String sha256,
            @RequestParam(defaultValue = "false") Boolean redacted) {
        AgentScopeAssert.assertScope(AgentTokenScope.ARTIFACT_WRITE);
        claimService.assertLeaseOwner(leaseId);
        return taskExecutionService.uploadArtifact("Bearer " + leaseToken, leaseId, file, caseId, stepId,
                purpose, sha256, redacted);
    }

    @PostMapping("/leases/{leaseId}/artifacts/{artifactId}:upload")
    public AgentExecutionArtifactUploadResponse uploadPreparedArtifact(
            @PathVariable String leaseId,
            @PathVariable String artifactId,
            @RequestHeader(LEASE_TOKEN_HEADER) String leaseToken,
            @RequestHeader(ARTIFACT_UPLOAD_TOKEN_HEADER) String uploadToken,
            @RequestParam("file") MultipartFile file) {
        AgentScopeAssert.assertScope(AgentTokenScope.ARTIFACT_WRITE);
        claimService.assertLeaseOwner(leaseId);
        return taskExecutionService.uploadPreparedArtifact(
                "Bearer " + leaseToken, leaseId, artifactId, uploadToken, file);
    }
}
