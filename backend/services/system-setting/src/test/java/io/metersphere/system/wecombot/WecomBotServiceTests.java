package io.metersphere.system.wecombot;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WecomBotServiceTests {
    private JdbcTemplate jdbc;
    private WecomBotService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new WecomBotService(jdbc, mock(WecomSecretService.class), mock(WecomBotBridgeClient.class),
                mock(NotificationTriggerProviderRegistry.class), mock(WecomNotificationCronScheduleService.class),
                mock(ApplicationEventPublisher.class));
    }

    @Test
    void rejectsCompetingSecretSourcesBeforePersisting() {
        when(jdbc.queryForList("SELECT * FROM wecom_bot_config ORDER BY create_time LIMIT 1")).thenReturn(List.of());

        assertThrows(MSException.class,
                () -> service.saveConfig(new WecomBotModels.ConfigRequest("bot", "bot-id", "raw", "env:BOT_SECRET"), "user-1"));
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void successfulDeliveryCannotBeDowngradedByALateFailure() {
        String sql = "SELECT status,attempts,max_attempts,target_type,target_id FROM wecom_notification_outbox WHERE id=?";
        when(jdbc.queryForList(sql, "outbox-1")).thenReturn(List.of(Map.of(
                "status", "SUCCESS", "attempts", 1, "max_attempts", 4, "target_type", "CHAT", "target_id", "group-1")));

        service.finishDelivery("outbox-1", false, true, "BRIDGE_UNAVAILABLE", "late response failure");

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void rendersOnlySupportedTemplateVariables() {
        assertEquals("Rule: daily", service.render("Rule: ${ruleName}", Map.of("ruleName", "daily")));
        assertThrows(MSException.class, () -> service.render("${secret}", Map.of("secret", "must-not-render")));
    }
}
