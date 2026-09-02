package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunnerLeaseAuthorizationServiceTests {
    @Mock
    private AgentExecutionMapper executionMapper;
    @InjectMocks
    private AgentRunnerLeaseAuthorizationService service;

    @Test
    void acceptsMatchingActiveLeaseForNonTerminalTask() {
        AgentRunnerLeaseDTO lease = activeLease("msrl_secret");
        AgentExecutionTaskDTO task = activeTask();
        when(executionMapper.selectLeaseById("lease-1")).thenReturn(lease);
        when(executionMapper.selectTaskById("task-1")).thenReturn(task);

        assertSame(lease, service.requireActiveLease("Bearer msrl_secret", "lease-1"));
    }

    @Test
    void rejectsMismatchedLeaseToken() {
        when(executionMapper.selectLeaseById("lease-1")).thenReturn(activeLease("msrl_secret"));

        MSException error = assertThrows(MSException.class,
                () -> service.requireActiveLease("Bearer msrl_wrong", "lease-1"));

        assertEquals("RUNNER_LEASE_UNAUTHORIZED", error.getMessage());
    }

    @Test
    void rejectsLeaseThatNoLongerOwnsTask() {
        AgentExecutionTaskDTO task = activeTask();
        task.setRunnerLeaseId("lease-2");
        when(executionMapper.selectLeaseById("lease-1")).thenReturn(activeLease("msrl_secret"));
        when(executionMapper.selectTaskById("task-1")).thenReturn(task);

        MSException error = assertThrows(MSException.class,
                () -> service.requireActiveLease("Bearer msrl_secret", "lease-1"));

        assertEquals("RUNNER_LEASE_TASK_MISMATCH", error.getMessage());
    }

    private AgentRunnerLeaseDTO activeLease(String token) {
        AgentRunnerLeaseDTO lease = new AgentRunnerLeaseDTO();
        lease.setId("lease-1");
        lease.setTaskId("task-1");
        lease.setStatus("ACTIVE");
        lease.setExpireTime(System.currentTimeMillis() + 60_000L);
        lease.setLeaseTokenHash(hash(token));
        return lease;
    }

    private AgentExecutionTaskDTO activeTask() {
        AgentExecutionTaskDTO task = new AgentExecutionTaskDTO();
        task.setId("task-1");
        task.setRunnerLeaseId("lease-1");
        task.setStatus("RUNNING");
        return task;
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
