package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentTaskTriggerDTO;
import io.metersphere.agent.mapper.AgentTaskTriggerMapper;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTaskTriggerServiceTests {

    @Test
    void webhookRejectsExpiredRequestBeforeDispatch() {
        AgentTaskTriggerMapper mapper = mock(AgentTaskTriggerMapper.class);
        AgentTaskTriggerDTO trigger = new AgentTaskTriggerDTO();
        trigger.setId("trigger-1");
        trigger.setEnabled(true);
        trigger.setTriggerType("EVENT");
        trigger.setWebhookSecretCipher("not-needed-for-expired-request");
        when(mapper.selectById("trigger-1")).thenReturn(trigger);
        AgentTaskTriggerService service = new AgentTaskTriggerService();
        ReflectionTestUtils.setField(service, "mapper", mapper);

        MSException error = assertThrows(MSException.class, () -> service.webhook(
                "trigger-1", "event-1", "1", "sha256=invalid", "{\"eventType\":\"BUILD\"}"));

        assertEquals("WEBHOOK_TIMESTAMP_EXPIRED", error.getMessage());
    }

    @Test
    void webhookRequiresSignedHeaders() {
        AgentTaskTriggerMapper mapper = mock(AgentTaskTriggerMapper.class);
        AgentTaskTriggerDTO trigger = new AgentTaskTriggerDTO();
        trigger.setId("trigger-1");
        trigger.setEnabled(true);
        trigger.setTriggerType("EVENT");
        when(mapper.selectById("trigger-1")).thenReturn(trigger);
        AgentTaskTriggerService service = new AgentTaskTriggerService();
        ReflectionTestUtils.setField(service, "mapper", mapper);

        MSException error = assertThrows(MSException.class,
                () -> service.webhook("trigger-1", "", "", "", ""));

        assertEquals("WEBHOOK_SIGNATURE_HEADERS_REQUIRED", error.getMessage());
    }
}
