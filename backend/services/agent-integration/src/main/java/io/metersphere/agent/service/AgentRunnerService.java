package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentExecutionStatus;
import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentRunnerDTO;
import io.metersphere.agent.dto.AgentRunnerHeartbeatRequest;
import io.metersphere.agent.dto.AgentRunnerEventsRequest;
import io.metersphere.agent.dto.AgentRunnerControlDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseAssignmentDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseCompleteRequest;
import io.metersphere.agent.dto.AgentRunnerLeaseDTO;
import io.metersphere.agent.dto.AgentRunnerPollRequest;
import io.metersphere.agent.dto.AgentRunnerRegisterRequest;
import io.metersphere.agent.dto.AgentRunnerRegisterResponse;
import io.metersphere.agent.dto.AgentRunnerTaskStateRequest;
import io.metersphere.agent.dto.AgentExecutionEventDTO;
import io.metersphere.agent.dto.AgentExecutionHealingDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentRunnerService {
    private static final String CONTRACT_VERSION = "v1";
    private static final long RUNNER_HEARTBEAT_STALE_MS = 90_000L;
    private static final long LEASE_TTL_MS = 60_000L;
    private static final Set<String> RUNNER_EVENT_TYPES = Set.of(
            "TASK_ACCEPTED", "BROWSER_READY", "LOGIN_REQUIRED", "CASE_STARTED", "STEP_STARTED",
            "ACTION_COMPLETED", "ASSERTION_FAILED", "HEALING_STARTED", "HEALING_COMPLETED",
            "STEP_COMPLETED", "CASE_COMPLETED", "RUNNER_FAILED", "TASK_EXECUTION_COMPLETED",
            "PAGE_ERROR", "CONSOLE_ERROR");
    private static final Set<String> ISOLATION_MODES = Set.of("UNDECLARED", "PROCESS", "CONTAINER", "VM");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Resource
    private AgentExecutionMapper executionMapper;
    @Resource
    private AgentExecLogService execLogService;
    @Resource
    private AgentExecutionWritebackService writebackService;
    @Resource
    private AgentHumanRequestService humanRequestService;

    public AgentRunnerRegisterResponse register(AgentRunnerRegisterRequest request) {
        if (!CONTRACT_VERSION.equals(request.getContractVersion())) {
            throw new MSException("UNSUPPORTED_CONTRACT_VALUE: runner.contractVersion");
        }
        String userId = requireUserId();
        long now = System.currentTimeMillis();
        String token = "msrt_" + randomToken();
        AgentRunnerDTO runner = new AgentRunnerDTO();
        runner.setId(IDGenerator.nextStr());
        runner.setOrganizationId(SessionUtils.getCurrentOrganizationId());
        runner.setName(StringUtils.trim(request.getName()));
        runner.setRunnerVersion(StringUtils.trim(request.getRunnerVersion()));
        runner.setContractVersion(CONTRACT_VERSION);
        runner.setStatus("OFFLINE");
        runner.setOperatingSystem(StringUtils.trimToNull(request.getOperatingSystem()));
        runner.setBrowserCapabilities(StringUtils.trimToNull(request.getBrowserCapabilities()));
        runner.setEnvironmentLabels(StringUtils.trimToNull(request.getEnvironmentLabels()));
        String isolationMode = StringUtils.upperCase(StringUtils.defaultIfBlank(request.getIsolationMode(), "UNDECLARED"));
        if (!ISOLATION_MODES.contains(isolationMode)) {
            throw new MSException("RUNNER_ISOLATION_MODE_INVALID: only UNDECLARED/PROCESS/CONTAINER/VM are supported");
        }
        runner.setIsolationMode(isolationMode);
        runner.setAuthTokenHash(hash(token));
        runner.setMaxConcurrency(request.getMaxConcurrency() == null ? 1 : request.getMaxConcurrency());
        runner.setActiveCount(0);
        runner.setCreateTime(now);
        runner.setUpdateTime(now);
        runner.setCreateUser(userId);
        runner.setUpdateUser(userId);
        executionMapper.insertRunner(runner);
        execLogService.audit("AI_RUNNER_REGISTER", runner.getId(), "contractVersion=" + CONTRACT_VERSION);

        AgentRunnerRegisterResponse response = new AgentRunnerRegisterResponse();
        response.setRunnerId(runner.getId());
        response.setRunnerToken(token);
        response.setContractVersion(CONTRACT_VERSION);
        response.setCreateTime(now);
        return response;
    }

    @Transactional(readOnly = true)
    public List<AgentRunnerDTO> list() {
        String organizationId = SessionUtils.getCurrentOrganizationId();
        if (StringUtils.isBlank(organizationId)) {
            throw new MSException("Unable to resolve current organization");
        }
        long now = System.currentTimeMillis();
        return executionMapper.selectRunnersByOrganization(organizationId, now, now - RUNNER_HEARTBEAT_STALE_MS);
    }

    public void heartbeat(String authorization, AgentRunnerHeartbeatRequest request) {
        AgentRunnerDTO runner = authenticate(authorization, request.getRunnerId());
        int active = request.getActiveCount() == null ? 0 : request.getActiveCount();
        if (active > runner.getMaxConcurrency()) {
            throw new MSException("RUNNER_CAPACITY_INVALID: activeCount exceeds maxConcurrency");
        }
        int updated = executionMapper.updateRunnerHeartbeat(runner.getId(), "ONLINE", active, System.currentTimeMillis());
        if (updated != 1) {
            throw new MSException("RUNNER_NOT_FOUND");
        }
    }

    public AgentRunnerLeaseAssignmentDTO poll(String authorization, AgentRunnerPollRequest request) {
        AgentRunnerDTO runner = authenticate(authorization, request.getRunnerId());
        long now = System.currentTimeMillis();
        if (runner.getLastHeartbeatTime() == null || runner.getLastHeartbeatTime() < now - RUNNER_HEARTBEAT_STALE_MS) {
            throw new MSException("RUNNER_HEARTBEAT_STALE");
        }
        int activeLeases = executionMapper.countActiveRunnerLeases(runner.getId(), now);
        if (runner.getMaxConcurrency() != null && activeLeases >= runner.getMaxConcurrency()) {
            return null;
        }
        AgentExecutionTaskDTO task = executionMapper.selectQueuedTaskForRunner(runner.getOrganizationId(), runner.getId());
        if (task == null) {
            return null;
        }
        String leaseToken = "msrl_" + randomToken();
        AgentRunnerLeaseDTO lease = new AgentRunnerLeaseDTO();
        lease.setId(IDGenerator.nextStr());
        lease.setTaskId(task.getId());
        lease.setRunnerId(runner.getId());
        lease.setExecutorType("RUNNER");
        lease.setExecutorId(runner.getId());
        lease.setAttempt((task.getAttemptCount() == null ? 0 : task.getAttemptCount()) + 1);
        lease.setStatus("ACTIVE");
        lease.setLeaseTokenHash(hash(leaseToken));
        lease.setAcceptedTime(now);
        lease.setExpireTime(now + LEASE_TTL_MS);
        lease.setLastHeartbeatTime(now);
        Long currentSequence = executionMapper.selectMaxEventSequence(task.getId());
        lease.setLastEventSequence(currentSequence == null ? 0L : currentSequence);
        lease.setCreateTime(now);
        lease.setUpdateTime(now);
        lease.setVersion(0);

        int assigned = executionMapper.assignRunnerLease(task.getId(), AgentExecutionStatus.QUEUED,
                task.getVersion() == null ? 0 : task.getVersion(), runner.getId(), lease.getId(),
                AgentExecutionStatus.PREPARING_BROWSER, now);
        if (assigned != 1) {
            throw new MSException("RUNNER_LEASE_CONFLICT: task already assigned");
        }
        executionMapper.insertRunnerLease(lease);
        execLogService.audit("AI_RUNNER_LEASE_ASSIGNED", task.getId(), "runnerId=" + runner.getId());

        AgentRunnerLeaseAssignmentDTO response = new AgentRunnerLeaseAssignmentDTO();
        response.setLeaseId(lease.getId());
        response.setLeaseToken(leaseToken);
        response.setExpireTime(lease.getExpireTime());
        response.setNextEventSequence(lease.getLastEventSequence() + 1);
        response.setTask(hydrate(task));
        response.getTask().setStatus(AgentExecutionStatus.PREPARING_BROWSER);
        response.getTask().setRunnerId(runner.getId());
        response.getTask().setRunnerLeaseId(lease.getId());
        response.getTask().setVersion((task.getVersion() == null ? 0 : task.getVersion()) + 1);
        return response;
    }

    public void renewLease(String authorization, String leaseId) {
        AgentRunnerLeaseDTO lease = authenticateLease(authorization, leaseId);
        requireLeaseTaskActive(lease);
        long now = System.currentTimeMillis();
        int updated = executionMapper.renewRunnerLease(leaseId, lease.getRunnerId(),
                lease.getVersion() == null ? 0 : lease.getVersion(), now + LEASE_TTL_MS, now);
        if (updated != 1) {
            throw new MSException("RUNNER_LEASE_CONFLICT");
        }
    }

    public AgentRunnerControlDTO control(String authorization, String leaseId) {
        AgentRunnerLeaseDTO lease = authenticateLease(authorization, leaseId);
        AgentExecutionTaskDTO task = executionMapper.selectTaskById(lease.getTaskId());
        if (task == null || !leaseId.equals(task.getRunnerLeaseId())) {
            throw new MSException("RUNNER_LEASE_TASK_MISMATCH");
        }
        String command = switch (task.getStatus()) {
            case AgentExecutionStatus.CANCELED -> "CANCEL";
            case AgentExecutionStatus.PAUSED -> "PAUSE";
            case AgentExecutionStatus.WAITING_LOGIN -> "WAIT_LOGIN";
            case AgentExecutionStatus.WAITING_HUMAN -> "WAIT_HUMAN";
            case AgentExecutionStatus.RUNNING -> "CONTINUE";
            default -> "NONE";
        };
        return new AgentRunnerControlDTO(task.getStatus(), command, System.currentTimeMillis());
    }

    public void reportEvents(String authorization, AgentRunnerEventsRequest request) {
        AgentRunnerLeaseDTO lease = authenticateLease(authorization, request.getLeaseId());
        requireLeaseTaskActive(lease);
        if (request.getEvents().size() > 100) {
            throw new MSException("RUNNER_EVENT_BATCH_TOO_LARGE");
        }
        long lastAccepted = lease.getLastEventSequence() == null ? 0L : lease.getLastEventSequence();
        long expected = lastAccepted + 1;
        Set<String> taskCaseIds = executionMapper.selectCasesByTaskId(lease.getTaskId()).stream()
                .map(AgentExecutionCaseDTO::getCaseId).collect(Collectors.toSet());
        List<AgentExecutionStepDTO> taskSteps = executionMapper.selectStepsByTaskId(lease.getTaskId());
        Map<String, String> stepCaseIds = taskSteps.stream()
                .collect(Collectors.toMap(AgentExecutionStepDTO::getId, AgentExecutionStepDTO::getCaseId));
        Map<String, String> stepExecutionCaseIds = taskSteps.stream()
                .collect(Collectors.toMap(AgentExecutionStepDTO::getId, AgentExecutionStepDTO::getExecutionCaseId));
        for (AgentExecutionEventDTO source : request.getEvents()) {
            if (source.getSequence() == null) {
                throw new MSException("RUNNER_EVENT_SEQUENCE_REQUIRED");
            }
            if (source.getSequence() <= lastAccepted) {
                continue;
            }
            if (source.getSequence() != expected) {
                throw new MSException("RUNNER_EVENT_SEQUENCE_GAP: expected " + expected);
            }
            if (!CONTRACT_VERSION.equals(source.getContractVersion())) {
                throw new MSException("UNSUPPORTED_CONTRACT_VALUE: event.contractVersion");
            }
            if (StringUtils.isBlank(source.getEventId()) || source.getEventId().length() > 64) {
                throw new MSException("RUNNER_EVENT_ID_INVALID");
            }
            String eventType = StringUtils.upperCase(StringUtils.trimToEmpty(source.getEventType()));
            if (!RUNNER_EVENT_TYPES.contains(eventType)) {
                throw new MSException("UNSUPPORTED_CONTRACT_VALUE: event.eventType");
            }
            validateEventScope(eventType, source.getCaseId(), source.getStepId(), taskCaseIds, stepCaseIds);
            AgentExecutionEventDTO event = new AgentExecutionEventDTO();
            event.setId(IDGenerator.nextStr());
            event.setContractVersion(CONTRACT_VERSION);
            event.setEventId(source.getEventId());
            event.setTaskId(lease.getTaskId());
            event.setCaseId(StringUtils.trimToNull(source.getCaseId()));
            event.setStepId(StringUtils.trimToNull(source.getStepId()));
            event.setAttempt(source.getAttempt() == null ? 0 : Math.max(0, Math.min(source.getAttempt(), 10)));
            event.setSequence(source.getSequence());
            long now = System.currentTimeMillis();
            event.setEventTime(source.getEventTime() == null || Math.abs(source.getEventTime() - now) > 86_400_000L
                    ? now : source.getEventTime());
            event.setLevel(normalizeEventLevel(source.getLevel()));
            event.setEventType(eventType);
            event.setMessage(StringUtils.abbreviate(sanitize(source.getMessage()), 2048));
            event.setArtifactIds(validateArtifacts(lease.getTaskId(), source.getArtifactIds()));
            event.setArtifactIdsJson(event.getArtifactIds().isEmpty() ? null : JSON.toJSONString(event.getArtifactIds()));
            event.setSanitizedMetadata(StringUtils.abbreviate(sanitize(source.getSanitizedMetadata()), 65_535));
            event.setCreateUser("runner:" + lease.getRunnerId());
            executionMapper.insertEvent(event);
            applyRuntimeEvent(event, stepExecutionCaseIds);
            expected++;
        }
        if (expected == lastAccepted + 1) {
            return;
        }
        long lastSequence = expected - 1;
        int updated = executionMapper.updateLeaseEventSequence(lease.getId(), lease.getRunnerId(),
                lease.getVersion() == null ? 0 : lease.getVersion(), lastAccepted, lastSequence, System.currentTimeMillis());
        if (updated != 1) {
            throw new MSException("RUNNER_LEASE_CONFLICT");
        }
    }

    public void updateTaskState(String authorization, String leaseId, AgentRunnerTaskStateRequest request) {
        AgentRunnerLeaseDTO lease = authenticateLease(authorization, leaseId);
        AgentExecutionTaskDTO task = requireLeaseTaskActive(lease);
        String toStatus = StringUtils.upperCase(request.getStatus());
        if (!List.of(AgentExecutionStatus.RUNNING, AgentExecutionStatus.WAITING_LOGIN,
                AgentExecutionStatus.WAITING_HUMAN,
                AgentExecutionStatus.WRITING_BACK, AgentExecutionStatus.FAILED).contains(toStatus)) {
            throw new MSException("UNSUPPORTED_CONTRACT_VALUE: runner task status");
        }
        AgentExecutionStateMachine.requireTransition(task.getStatus(), toStatus);
        int updated = executionMapper.transitionTaskStatus(task.getId(), task.getStatus(),
                task.getVersion() == null ? 0 : task.getVersion(), toStatus,
                "runner:" + lease.getRunnerId(), System.currentTimeMillis());
        if (updated != 1) {
            throw new MSException("RUNNER_TASK_STATE_CONFLICT");
        }
        if (AgentExecutionStatus.WAITING_LOGIN.equals(toStatus)) {
            humanRequestService.create(task.getId(), task.getProjectId(), "LOGIN", "需要人工登录",
                    sanitize(request.getReason()), "MEDIUM", "executor:" + lease.getRunnerId(),
                    task.getExecutedBy(), task.getTimeoutAt());
        }
        execLogService.audit("AI_RUNNER_TASK_STATE", task.getId(),
                task.getStatus() + "->" + toStatus + ";" + sanitize(request.getReason()));
    }

    public void completeLease(String authorization, String leaseId, AgentRunnerLeaseCompleteRequest request) {
        AgentRunnerLeaseDTO lease = authenticateLease(authorization, leaseId);
        AgentExecutionTaskDTO task = executionMapper.selectTaskById(lease.getTaskId());
        if (task == null || !leaseId.equals(task.getRunnerLeaseId())) {
            throw new MSException("RUNNER_LEASE_TASK_MISMATCH");
        }
        String outcome = StringUtils.upperCase(StringUtils.trimToEmpty(request.getOutcome()));
        String leaseStatus;
        if ("COMPLETED".equals(outcome)) {
            if (AgentExecutionStatus.RUNNING.equals(task.getStatus())) {
                transitionRunnerTask(task, AgentExecutionStatus.WRITING_BACK, lease.getRunnerId());
                task.setStatus(AgentExecutionStatus.WRITING_BACK);
            } else if (!AgentExecutionStatus.WRITING_BACK.equals(task.getStatus())) {
                throw new MSException("RUNNER_TASK_NOT_READY_FOR_COMPLETION");
            }
            writebackService.writeback(task.getId());
            leaseStatus = "COMPLETED";
        } else if ("FAILED".equals(outcome)) {
            if (!AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
                transitionRunnerTask(task, AgentExecutionStatus.FAILED, lease.getRunnerId());
            }
            leaseStatus = "FAILED";
        } else if ("CANCELED".equals(outcome) && AgentExecutionStatus.CANCELED.equals(task.getStatus())) {
            leaseStatus = "CANCELED";
        } else {
            throw new MSException("UNSUPPORTED_CONTRACT_VALUE: lease.outcome");
        }
        int closed = executionMapper.closeRunnerLease(leaseId, lease.getRunnerId(), normalizedVersion(lease),
                leaseStatus, System.currentTimeMillis());
        if (closed != 1) {
            throw new MSException("RUNNER_LEASE_CONFLICT");
        }
        execLogService.audit("AI_RUNNER_LEASE_CLOSED", task.getId(),
                "outcome=" + outcome + ";" + sanitize(request.getReason()));
    }

    @Scheduled(fixedDelay = 30_000L)
    public void expireLeases() {
        long now = System.currentTimeMillis();
        for (AgentRunnerLeaseDTO lease : executionMapper.selectExpiredActiveLeases(now, 100)) {
            int recovered = executionMapper.recoverExpiredTaskLease(lease.getTaskId(), lease.getId(), now);
            executionMapper.closeRunnerLease(lease.getId(), lease.getRunnerId(), normalizedVersion(lease), "EXPIRED", now);
            AgentExecutionTaskDTO recoveredTask = executionMapper.selectTaskById(lease.getTaskId());
            String outcome = recoveredTask == null ? "MISSING" : recoveredTask.getStatus();
            execLogService.audit("AI_RUNNER_LEASE_EXPIRED", lease.getTaskId(),
                    "leaseId=" + lease.getId() + ";recovered=" + recovered + ";outcome=" + outcome);
        }
    }

    public AgentRunnerLeaseDTO requireActiveLease(String authorization, String leaseId) {
        AgentRunnerLeaseDTO lease = authenticateLease(authorization, leaseId);
        requireLeaseTaskActive(lease);
        return lease;
    }

    private AgentRunnerDTO authenticate(String authorization, String runnerId) {
        String token = bearerToken(authorization, "msrt_");
        AgentRunnerDTO runner = executionMapper.selectRunnerById(runnerId);
        if (runner == null || StringUtils.isBlank(runner.getAuthTokenHash()) || !MessageDigest.isEqual(
                hash(token).getBytes(StandardCharsets.UTF_8),
                runner.getAuthTokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw new MSException("RUNNER_UNAUTHORIZED");
        }
        return runner;
    }

    private AgentRunnerLeaseDTO authenticateLease(String authorization, String leaseId) {
        String token = bearerToken(authorization, "msrl_");
        AgentRunnerLeaseDTO lease = executionMapper.selectLeaseById(leaseId);
        if (lease == null || StringUtils.isBlank(lease.getLeaseTokenHash()) || !MessageDigest.isEqual(
                hash(token).getBytes(StandardCharsets.UTF_8),
                lease.getLeaseTokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw new MSException("RUNNER_LEASE_UNAUTHORIZED");
        }
        if (!"ACTIVE".equals(lease.getStatus()) || lease.getExpireTime() == null
                || lease.getExpireTime() < System.currentTimeMillis()) {
            throw new MSException("RUNNER_LEASE_EXPIRED");
        }
        return lease;
    }

    private AgentExecutionTaskDTO hydrate(AgentExecutionTaskDTO task) {
        List<AgentExecutionCaseDTO> cases = executionMapper.selectCasesByTaskId(task.getId());
        Map<String, List<AgentExecutionStepDTO>> stepsByCase = executionMapper.selectStepsByTaskId(task.getId()).stream()
                .collect(Collectors.groupingBy(AgentExecutionStepDTO::getExecutionCaseId,
                        LinkedHashMap::new, Collectors.toList()));
        cases.forEach(item -> item.setSteps(stepsByCase.getOrDefault(item.getId(), List.of())));
        task.setCases(cases);
        return task;
    }

    private void applyRuntimeEvent(AgentExecutionEventDTO event, Map<String, String> stepExecutionCaseIds) {
        long time = event.getEventTime();
        switch (event.getEventType()) {
            case "CASE_STARTED" -> executionMapper.markCaseStarted(event.getTaskId(), event.getCaseId(), time);
            case "STEP_STARTED" -> executionMapper.markStepStarted(event.getTaskId(), event.getStepId(),
                    event.getAttempt() == null ? 0 : event.getAttempt(), time);
            case "HEALING_STARTED" -> {
                executionMapper.markStepHealing(event.getTaskId(), event.getStepId(), time);
                persistHealingStarted(event, stepExecutionCaseIds.get(event.getStepId()));
            }
            case "HEALING_COMPLETED" -> {
                executionMapper.markStepHealingCompleted(event.getTaskId(), event.getStepId(), time);
                completeHealing(event, "SUCCESS");
            }
            case "ASSERTION_FAILED" -> {
                executionMapper.markStepCompleted(event.getTaskId(), event.getStepId(),
                        AgentExecutionStatus.FAILED, null, event.getMessage(), failureCategory(event), false, time);
                completeHealing(event, "FAILED");
            }
            case "STEP_COMPLETED" -> {
                boolean needsReview = "ERROR".equals(event.getLevel()) || StringUtils.contains(event.getMessage(), "人工");
                executionMapper.markStepCompleted(event.getTaskId(), event.getStepId(),
                        needsReview ? AgentExecutionStatus.CASE_NEEDS_REVIEW : AgentExecutionStatus.SUCCESS,
                        needsReview ? null : event.getMessage(), needsReview ? event.getMessage() : null,
                        needsReview ? "SCOPE_STEP_AMBIGUOUS" : null, false, time);
            }
            case "CASE_COMPLETED" -> {
                boolean failed = !"INFO".equals(event.getLevel());
                executionMapper.markCaseCompleted(event.getTaskId(), event.getCaseId(),
                        failed ? AgentExecutionStatus.FAILED : AgentExecutionStatus.SUCCESS,
                        failed ? "ERROR" : "SUCCESS", failed ? event.getMessage() : null, time);
            }
            default -> {
                // Other event types are facts retained for audit and do not directly mutate runtime rows.
            }
        }
    }

    private void persistHealingStarted(AgentExecutionEventDTO event, String executionCaseId) {
        if (StringUtils.isBlank(executionCaseId)) {
            throw new MSException("RUNNER_EVENT_STEP_MISMATCH");
        }
        Map<?, ?> metadata;
        try {
            metadata = JSON.parseMap(StringUtils.defaultString(event.getSanitizedMetadata(), "{}"));
        } catch (Exception ex) {
            throw new MSException("RUNNER_HEALING_METADATA_INVALID");
        }
        AgentExecutionHealingDTO healing = new AgentExecutionHealingDTO();
        healing.setId(IDGenerator.nextStr());
        healing.setTaskId(event.getTaskId());
        healing.setExecutionCaseId(executionCaseId);
        healing.setExecutionStepId(event.getStepId());
        healing.setAttempt(event.getAttempt() == null ? 0 : event.getAttempt());
        Object failureType = metadata.get("failureType");
        healing.setFailureType(StringUtils.abbreviate(
                failureType == null ? "LOCATOR_NOT_FOUND" : String.valueOf(failureType), 64));
        Object originalLocator = metadata.get("originalLocator");
        healing.setOriginalLocator(originalLocator == null ? null : JSON.toJSONString(originalLocator));
        Object selected = metadata.get("selectedLocator");
        String selectedLocator = selected == null ? null
                : StringUtils.abbreviate(StringUtils.trimToNull(String.valueOf(selected)), 2048);
        healing.setCandidateLocators(selectedLocator == null ? null : JSON.toJSONString(List.of(selectedLocator)));
        healing.setSelectedLocator(selectedLocator);
        healing.setReason(event.getMessage());
        healing.setConfidence(parseConfidence(metadata.get("confidence")));
        healing.setResult("STARTED");
        healing.setBeforeArtifactId(CollectionUtils.isEmpty(event.getArtifactIds()) ? null : event.getArtifactIds().getFirst());
        healing.setCreateTime(event.getEventTime());
        healing.setCreateUser(event.getCreateUser());
        executionMapper.insertHealing(healing);
    }

    private void completeHealing(AgentExecutionEventDTO event, String result) {
        executionMapper.completeHealing(event.getTaskId(), event.getStepId(),
                event.getAttempt() == null ? 0 : event.getAttempt(), result,
                CollectionUtils.isEmpty(event.getArtifactIds()) ? null : event.getArtifactIds().getFirst(),
                event.getEventTime());
    }

    private BigDecimal parseConfidence(Object value) {
        try {
            BigDecimal confidence = new BigDecimal(String.valueOf(value));
            return confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private String failureCategory(AgentExecutionEventDTO event) {
        try {
            Object value = JSON.parseMap(StringUtils.defaultString(event.getSanitizedMetadata())).get("failureCategory");
            return StringUtils.abbreviate(StringUtils.defaultIfBlank(String.valueOf(value), "ASSERTION_MISMATCH"), 64);
        } catch (Exception ignored) {
            return "ASSERTION_MISMATCH";
        }
    }

    private void validateEventScope(String eventType, String caseId, String stepId, Set<String> taskCaseIds,
                                    Map<String, String> stepCaseIds) {
        boolean caseEvent = Set.of("CASE_STARTED", "CASE_COMPLETED").contains(eventType);
        boolean stepEvent = Set.of("STEP_STARTED", "ACTION_COMPLETED", "ASSERTION_FAILED", "HEALING_STARTED",
                "HEALING_COMPLETED", "STEP_COMPLETED").contains(eventType);
        if ((caseEvent || stepEvent) && !taskCaseIds.contains(caseId)) {
            throw new MSException("RUNNER_EVENT_CASE_MISMATCH");
        }
        if (stepEvent && (!stepCaseIds.containsKey(stepId) || !StringUtils.equals(caseId, stepCaseIds.get(stepId)))) {
            throw new MSException("RUNNER_EVENT_STEP_MISMATCH");
        }
    }

    private void transitionRunnerTask(AgentExecutionTaskDTO task, String toStatus, String runnerId) {
        AgentExecutionStateMachine.requireTransition(task.getStatus(), toStatus);
        int updated = executionMapper.transitionTaskStatus(task.getId(), task.getStatus(),
                task.getVersion() == null ? 0 : task.getVersion(), toStatus,
                "runner:" + runnerId, System.currentTimeMillis());
        if (updated != 1) {
            throw new MSException("RUNNER_TASK_STATE_CONFLICT");
        }
    }

    private List<String> validateArtifacts(String taskId, List<String> artifactIds) {
        if (artifactIds == null || artifactIds.isEmpty()) {
            return List.of();
        }
        if (artifactIds.size() > 20) {
            throw new MSException("RUNNER_EVENT_ARTIFACT_LIMIT");
        }
        return artifactIds.stream().distinct().peek(id -> {
            AgentExecutionArtifactDTO artifact = executionMapper.selectArtifactById(id);
            if (artifact == null || !taskId.equals(artifact.getTaskId()) || !"AVAILABLE".equals(artifact.getStatus())) {
                throw new MSException("RUNNER_EVENT_ARTIFACT_MISMATCH");
            }
        }).toList();
    }

    private int normalizedVersion(AgentRunnerLeaseDTO lease) {
        return lease.getVersion() == null ? 0 : lease.getVersion();
    }

    private AgentExecutionTaskDTO requireLeaseTaskActive(AgentRunnerLeaseDTO lease) {
        AgentExecutionTaskDTO task = executionMapper.selectTaskById(lease.getTaskId());
        if (task == null || !StringUtils.equals(lease.getId(), task.getRunnerLeaseId())) {
            throw new MSException("RUNNER_LEASE_TASK_MISMATCH");
        }
        if (AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
            throw new MSException("RUNNER_LEASE_EXPIRED");
        }
        return task;
    }

    private String bearerToken(String authorization, String prefix) {
        if (StringUtils.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            throw new MSException("RUNNER_UNAUTHORIZED");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (!token.startsWith(prefix)) {
            throw new MSException("RUNNER_UNAUTHORIZED");
        }
        return token;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEventLevel(String level) {
        String normalized = StringUtils.upperCase(StringUtils.defaultIfBlank(level, "INFO"));
        return Set.of("DEBUG", "INFO", "WARN", "ERROR").contains(normalized) ? normalized : "INFO";
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("(?i)(authorization|cookie|set-cookie|password|passwd|token|secret)\\s*[:=]\\s*[^,;\\s}]+", "$1=***")
                .replaceAll("(?i)bearer\\s+[a-z0-9._~-]+", "Bearer ***");
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new MSException("RUNNER_TOKEN_HASH_FAILED");
        }
    }

    private String requireUserId() {
        String userId = SessionUtils.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new MSException("无法解析当前用户");
        }
        return userId;
    }
}
