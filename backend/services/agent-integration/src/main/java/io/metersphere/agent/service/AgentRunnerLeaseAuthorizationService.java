package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.constants.AgentExecutionStatus;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AgentRunnerLeaseAuthorizationService {
    @Resource
    private AgentExecutionMapper executionMapper;

    public AgentRunnerLeaseDTO requireActiveLease(String authorization, String leaseId) {
        AgentRunnerLeaseDTO lease = authenticateLease(authorization, leaseId);
        requireLeaseTaskActive(lease);
        return lease;
    }

    public AgentRunnerLeaseDTO authenticateLease(String authorization, String leaseId) {
        String token = bearerToken(authorization);
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

    public AgentExecutionTaskDTO requireLeaseTaskActive(AgentRunnerLeaseDTO lease) {
        AgentExecutionTaskDTO task = executionMapper.selectTaskById(lease.getTaskId());
        if (task == null || !StringUtils.equals(lease.getId(), task.getRunnerLeaseId())) {
            throw new MSException("RUNNER_LEASE_TASK_MISMATCH");
        }
        if (AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
            throw new MSException("RUNNER_LEASE_EXPIRED");
        }
        return task;
    }

    private String bearerToken(String authorization) {
        if (StringUtils.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            throw new MSException("RUNNER_UNAUTHORIZED");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (!token.startsWith("msrl_")) {
            throw new MSException("RUNNER_UNAUTHORIZED");
        }
        return token;
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new MSException("RUNNER_TOKEN_HASH_FAILED");
        }
    }
}
