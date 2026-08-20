package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentExecLog;
import io.metersphere.system.mapper.AgentExecLogMapper;
import io.metersphere.system.uid.IDGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentExecLogServiceTests {

    @Test
    void auditRejectsActionLongerThanDatabaseLimitWithSpecificMessage() {
        AgentExecLogService service = serviceWith(mock(AgentExecLogMapper.class));
        String action = "A".repeat(AgentExecLogService.AUDIT_ACTION_MAX_LENGTH + 1);

        MSException error = assertThrows(MSException.class,
                () -> service.audit(action, "resource-1", "{}"));

        assertEquals("审计动作码过长：当前长度 129，最大允许 128 个字符，请缩短动作码后重试", error.getMessage());
    }

    @Test
    void auditExplainsMissingDatabaseExpansion() {
        AgentExecLogMapper mapper = mock(AgentExecLogMapper.class);
        when(mapper.insert(any(AgentExecLog.class))).thenThrow(new DataIntegrityViolationException(
                "Data too long for column 'last_exec_result' at row 1"));
        AgentExecLogService service = serviceWith(mapper);

        MSException error;
        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("audit-log-1");
            error = assertThrows(MSException.class,
                    () -> service.audit("AI_TASK_TRIGGER_CREATE", "resource-1", "{}"));
        }

        assertTrue(error.getMessage().contains("数据库字段 last_exec_result 容量不足"));
        assertTrue(error.getMessage().contains("当前动作码长度：22"));
    }

    private AgentExecLogService serviceWith(AgentExecLogMapper mapper) {
        AgentExecLogService service = new AgentExecLogService();
        ReflectionTestUtils.setField(service, "agentExecLogMapper", mapper);
        return service;
    }
}
