package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentExecutionStepResultDTO;
import io.metersphere.agent.dto.AgentExecutionStepSubmitRequest;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.security.AgentSensitiveDataSanitizer;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentExecutionStepResultService {
    private static final Set<String> STATUSES = Set.of("SUCCESS", "FAILED", "BLOCKED", "SKIPPED");

    private final AgentExecutionMapper mapper;
    private final AgentRunnerService runnerService;

    public AgentExecutionStepResultService(AgentExecutionMapper mapper, AgentRunnerService runnerService) {
        this.mapper = mapper;
        this.runnerService = runnerService;
    }

    public AgentExecutionStepResultDTO submit(AgentExecutionStepSubmitRequest request) {
        if (request == null
                || StringUtils.isAnyBlank(request.getTaskId(), request.getExecutionId(), request.getLeaseId(),
                request.getLeaseToken(), request.getStepId(), request.getStatus(), request.getRequestId())) {
            throw new MSException("STEP_RESULT_REQUEST_INVALID");
        }
        AgentRunnerLeaseDTO lease = runnerService.requireActiveLease("Bearer " + request.getLeaseToken(), request.getLeaseId());
        AgentExecutionTaskDTO task = mapper.selectTaskById(request.getTaskId());
        if (task == null || !StringUtils.equals(lease.getTaskId(), task.getId())
                || !StringUtils.equals(lease.getExecutionId(), request.getExecutionId())
                || !StringUtils.equals(task.getRunnerLeaseId(), lease.getId())) {
            throw new MSException("TASK_NOT_FOUND_OR_NOT_ACCESSIBLE");
        }
        String status = StringUtils.upperCase(StringUtils.trim(request.getStatus()));
        if (!STATUSES.contains(status)) {
            throw new MSException("STEP_RESULT_STATUS_INVALID");
        }
        AgentExecutionStepDTO step = mapper.selectStepsByTaskId(task.getId()).stream()
                .filter(item -> StringUtils.equals(item.getId(), request.getStepId()))
                .findFirst().orElseThrow(() -> new MSException("STEP_NOT_FOUND_OR_NOT_ACCESSIBLE"));
        AgentExecutionStepResultDTO existing = mapper.selectStepResultByRequest(
                request.getExecutionId(), request.getStepId(), request.getRequestId());
        if (existing != null) {
            return existing;
        }
        List<String> artifactIds = request.getArtifactIds() == null ? List.of() : request.getArtifactIds();
        for (String artifactId : artifactIds) {
            AgentExecutionArtifactDTO artifact = mapper.selectArtifactById(artifactId);
            if (artifact == null || !StringUtils.equals(task.getId(), artifact.getTaskId())
                    || !StringUtils.equals(request.getExecutionId(), artifact.getExecutionId())
                    || !"AVAILABLE".equals(artifact.getStatus())) {
                throw new MSException("ARTIFACT_NOT_FOUND_OR_NOT_ACCESSIBLE");
            }
        }
        long now = System.currentTimeMillis();
        AgentExecutionStepResultDTO result = new AgentExecutionStepResultDTO();
        result.setId(IDGenerator.nextStr());
        result.setTaskId(task.getId());
        result.setExecutionId(request.getExecutionId());
        result.setLeaseId(lease.getId());
        result.setStepId(step.getId());
        result.setAttempt(request.getAttempt() == null ? lease.getAttempt() : request.getAttempt());
        result.setStatus(status);
        result.setInputSnapshot(StringUtils.abbreviate(
                AgentSensitiveDataSanitizer.sanitize(request.getInputSnapshot()), 65_535));
        result.setOutputSummary(StringUtils.abbreviate(
                AgentSensitiveDataSanitizer.sanitize(request.getOutputSummary()), 65_535));
        result.setAssertionResult(StringUtils.abbreviate(
                AgentSensitiveDataSanitizer.sanitize(request.getAssertionResult()), 65_535));
        result.setErrorCode(StringUtils.abbreviate(request.getErrorCode(), 64));
        result.setErrorMessage(StringUtils.abbreviate(
                AgentSensitiveDataSanitizer.sanitize(request.getErrorMessage()), 1000));
        result.setArtifactIds(artifactIds.isEmpty() ? null : JSON.toJSONString(artifactIds));
        result.setRequestId(request.getRequestId());
        result.setTraceId(StringUtils.defaultIfBlank(request.getTraceId(), task.getTraceId()));
        result.setStartedAt(request.getStartedAt());
        result.setFinishedAt(request.getFinishedAt() == null ? now : request.getFinishedAt());
        result.setCreateTime(now);
        result.setCreateUser("executor:" + lease.getLeaseOwnerId());
        mapper.insertStepResult(result);
        mapper.markStepCompleted(task.getId(), step.getId(), status, result.getOutputSummary(),
                result.getErrorMessage(), result.getErrorCode(), false, now);
        return result;
    }
}
