package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentExecutionStatus;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentExecutionTaskSearchResponse;
import io.metersphere.agent.dto.AgentHumanCreateRequest;
import io.metersphere.agent.dto.AgentHumanRequestDTO;
import io.metersphere.agent.dto.AgentRunnerControlDTO;
import io.metersphere.agent.dto.AgentRunnerEventsRequest;
import io.metersphere.agent.dto.AgentRunnerLeaseAssignmentDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseCompleteRequest;
import io.metersphere.agent.dto.AgentRunnerLeaseDTO;
import io.metersphere.agent.dto.AgentRunnerTaskStateRequest;
import io.metersphere.agent.dto.AgentTaskClaimRequest;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pull protocol for generic Agents authenticated by an Agent Token. */
@Service
@Transactional(rollbackFor = Exception.class)
public class AgentTaskClaimService {
    private static final long LEASE_TTL_MS = 60_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private AgentExecutionMapper executionMapper;
    @Resource
    private AgentExecutionService executionService;
    @Resource
    private AgentProjectService projectService;
    @Resource
    private AgentRunnerService runnerService;
    @Resource
    private AgentExecLogService execLogService;
    @Resource
    private AgentHumanRequestService humanRequestService;

    public AgentRunnerLeaseAssignmentDTO claim(AgentTaskClaimRequest request) {
        AgentToken token = requireToken();
        String projectId = projectService.resolveProjectId(request.getProjectId());
        String agentType = StringUtils.upperCase(StringUtils.trimToNull(request.getAgentType()));
        Set<String> offered = new LinkedHashSet<>();
        if (CollectionUtils.isNotEmpty(request.getCapabilities())) {
            request.getCapabilities().stream().filter(StringUtils::isNotBlank)
                    .map(String::trim).map(value -> value.toLowerCase(Locale.ROOT)).forEach(offered::add);
        }

        AgentExecutionTaskDTO task = executionMapper.selectQueuedTasksForAgent(projectId, agentType, 20).stream()
                .filter(candidate -> StringUtils.isBlank(request.getTaskId())
                        || StringUtils.equals(request.getTaskId(), candidate.getId()))
                .filter(candidate -> supports(candidate, offered))
                .findFirst().orElse(null);
        if (task == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        String leaseToken = "msrl_" + randomToken();
        String executorId = "agent-token:" + token.getId();
        AgentRunnerLeaseDTO lease = new AgentRunnerLeaseDTO();
        lease.setId(IDGenerator.nextStr());
        lease.setTaskId(task.getId());
        lease.setRunnerId(executorId);
        lease.setExecutorType("AGENT");
        lease.setExecutorId(token.getId());
        lease.setAttempt((task.getAttemptCount() == null ? 0 : task.getAttemptCount()) + 1);
        lease.setStatus("ACTIVE");
        lease.setLeaseTokenHash(DigestUtils.sha256Hex(leaseToken));
        lease.setAcceptedTime(now);
        lease.setExpireTime(now + LEASE_TTL_MS);
        lease.setLastHeartbeatTime(now);
        Long sequence = executionMapper.selectMaxEventSequence(task.getId());
        lease.setLastEventSequence(sequence == null ? 0L : sequence);
        lease.setCreateTime(now);
        lease.setUpdateTime(now);
        lease.setVersion(0);
        int assigned = executionMapper.assignRunnerLease(task.getId(), AgentExecutionStatus.QUEUED,
                task.getVersion() == null ? 0 : task.getVersion(), executorId, lease.getId(),
                AgentExecutionStatus.PREPARING_BROWSER, now);
        if (assigned != 1) {
            throw new MSException("AGENT_TASK_LEASE_CONFLICT");
        }
        executionMapper.insertRunnerLease(lease);
        execLogService.audit("AGENT_TASK_CLAIMED", task.getId(),
                "tokenId=" + token.getId() + ";attempt=" + lease.getAttempt());

        AgentRunnerLeaseAssignmentDTO response = new AgentRunnerLeaseAssignmentDTO();
        response.setLeaseId(lease.getId());
        response.setLeaseToken(leaseToken);
        response.setExpireTime(lease.getExpireTime());
        response.setNextEventSequence(lease.getLastEventSequence() + 1);
        response.setTask(executionService.get(task.getId()));
        response.getTask().setStatus(AgentExecutionStatus.PREPARING_BROWSER);
        response.getTask().setRunnerId(executorId);
        response.getTask().setRunnerLeaseId(lease.getId());
        return response;
    }

    public AgentExecutionTaskSearchResponse search(AgentTaskClaimRequest request) {
        String projectId = projectService.resolveProjectId(request.getProjectId());
        String agentType = StringUtils.upperCase(StringUtils.trimToNull(request.getAgentType()));
        Set<String> offered = normalizeCapabilities(request.getCapabilities());
        List<AgentExecutionTaskDTO> tasks = executionMapper.selectQueuedTasksForAgent(projectId, agentType, 100)
                .stream().filter(candidate -> supports(candidate, offered)).toList();
        AgentExecutionTaskSearchResponse response = new AgentExecutionTaskSearchResponse();
        response.setCurrent(1);
        response.setPageSize(100);
        response.setTotal(tasks.size());
        response.setItems(tasks);
        return response;
    }

    public void heartbeat(String leaseId, String leaseToken) {
        assertLeaseOwner(leaseId);
        runnerService.renewLease(bearer(leaseToken), leaseId);
    }

    public void reportEvents(String leaseId, String leaseToken, AgentRunnerEventsRequest request) {
        assertLeaseOwner(leaseId);
        runnerService.reportEvents(bearer(leaseToken), request);
    }

    public void updateState(String leaseId, String leaseToken, AgentRunnerTaskStateRequest request) {
        assertLeaseOwner(leaseId);
        runnerService.updateTaskState(bearer(leaseToken), leaseId, request);
    }

    public void complete(String leaseId, String leaseToken, AgentRunnerLeaseCompleteRequest request) {
        assertLeaseOwner(leaseId);
        runnerService.completeLease(bearer(leaseToken), leaseId, request);
    }

    public AgentRunnerControlDTO control(String taskId, String leaseId, String leaseToken) {
        AgentRunnerLeaseDTO lease = requireOwnedTaskLease(taskId, leaseId);
        return runnerService.control(bearer(leaseToken), lease.getId());
    }

    public AgentHumanRequestDTO requestHuman(String taskId, String leaseId, String leaseToken,
                                             AgentHumanCreateRequest request) {
        AgentRunnerLeaseDTO lease = requireOwnedTaskLease(taskId, leaseId);
        runnerService.requireActiveLease(bearer(leaseToken), leaseId);
        AgentExecutionTaskDTO task = executionMapper.selectTaskById(taskId);
        if (task == null) {
            throw new MSException("AGENT_TASK_NOT_FOUND");
        }
        String requestType = StringUtils.upperCase(StringUtils.trim(request.getRequestType()));
        String waitStatus = "LOGIN".equals(requestType)
                ? AgentExecutionStatus.WAITING_LOGIN : AgentExecutionStatus.WAITING_HUMAN;
        if (!StringUtils.equals(task.getStatus(), waitStatus)) {
            AgentExecutionStateMachine.requireTransition(task.getStatus(), waitStatus);
            int updated = executionMapper.transitionTaskStatus(taskId, task.getStatus(),
                    task.getVersion() == null ? 0 : task.getVersion(), waitStatus,
                    "executor:" + lease.getExecutorId(), System.currentTimeMillis());
            if (updated != 1) {
                throw new MSException("AGENT_TASK_STATE_CONFLICT");
            }
        }
        AgentHumanRequestDTO created = humanRequestService.createFromAgent(taskId, task.getProjectId(), request,
                "executor:" + lease.getExecutorId(), task.getExecutedBy());
        execLogService.audit("AGENT_HUMAN_REQUEST_CREATED", taskId,
                "requestId=" + request.getRequestId() + ";type=" + requestType);
        return created;
    }

    public void release(String taskId, String leaseId, String leaseToken, String reason) {
        AgentRunnerLeaseDTO lease = requireOwnedTaskLease(taskId, leaseId);
        runnerService.requireActiveLease(bearer(leaseToken), leaseId);
        long now = System.currentTimeMillis();
        int released = executionMapper.releaseTaskLease(taskId, leaseId,
                StringUtils.abbreviate(StringUtils.defaultIfBlank(reason, "Agent cannot execute task"), 1000), now);
        if (released != 1) {
            throw new MSException("AGENT_TASK_RELEASE_CONFLICT");
        }
        int closed = executionMapper.closeRunnerLease(leaseId, lease.getRunnerId(),
                lease.getVersion() == null ? 0 : lease.getVersion(), "RELEASED", now);
        if (closed != 1) {
            throw new MSException("AGENT_TASK_LEASE_CONFLICT");
        }
        execLogService.audit("AGENT_TASK_RELEASED", taskId, StringUtils.abbreviate(reason, 1000));
    }

    private boolean supports(AgentExecutionTaskDTO task, Set<String> offered) {
        List<String> required;
        try {
            required = JSON.parseArray(StringUtils.defaultString(task.getRequiredCapabilities(), "[]"), String.class);
        } catch (Exception ex) {
            return false;
        }
        if (CollectionUtils.isEmpty(required)) {
            return true;
        }
        return required.stream().map(String::toLowerCase).allMatch(offered::contains);
    }

    private Set<String> normalizeCapabilities(List<String> capabilities) {
        Set<String> offered = new LinkedHashSet<>();
        if (CollectionUtils.isNotEmpty(capabilities)) {
            capabilities.stream().filter(StringUtils::isNotBlank).map(String::trim)
                    .map(value -> value.toLowerCase(Locale.ROOT)).forEach(offered::add);
        }
        return offered;
    }

    public void assertLeaseOwner(String leaseId) {
        AgentRunnerLeaseDTO lease = executionMapper.selectLeaseById(leaseId);
        AgentToken token = requireToken();
        if (lease == null || !"AGENT".equals(lease.getExecutorType())
                || !StringUtils.equals(token.getId(), lease.getExecutorId())) {
            throw new MSException("AGENT_TASK_LEASE_FORBIDDEN");
        }
    }

    private AgentRunnerLeaseDTO requireOwnedTaskLease(String taskId, String leaseId) {
        assertLeaseOwner(leaseId);
        AgentRunnerLeaseDTO lease = executionMapper.selectLeaseById(leaseId);
        if (!StringUtils.equals(taskId, lease.getTaskId())) {
            throw new MSException("AGENT_TASK_LEASE_TASK_MISMATCH");
        }
        return lease;
    }

    private AgentToken requireToken() {
        AgentToken token = AgentTokenContext.get();
        if (token == null || StringUtils.isBlank(token.getId())) {
            throw new MSException("AGENT_TOKEN_REQUIRED");
        }
        return token;
    }

    private String bearer(String leaseToken) {
        if (StringUtils.isBlank(leaseToken)) {
            throw new MSException("AGENT_TASK_LEASE_TOKEN_REQUIRED");
        }
        return "Bearer " + leaseToken.trim();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
