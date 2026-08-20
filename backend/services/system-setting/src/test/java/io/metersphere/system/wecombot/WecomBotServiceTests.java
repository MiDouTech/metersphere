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
    private WecomSecretService secrets;
    private WecomBotBridgeClient bridge;
    private NotificationTriggerProviderRegistry providers;
    private WecomBotService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        secrets = mock(WecomSecretService.class);
        bridge = mock(WecomBotBridgeClient.class);
        providers = mock(NotificationTriggerProviderRegistry.class);
        service = new WecomBotService(jdbc, secrets, bridge,
                providers, mock(WecomNotificationCronScheduleService.class),
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

    @Test
    void restoresEnabledBotConnectionAfterApplicationStartup() {
        Map<String, Object> config = Map.of(
                "id", "config-1",
                "bot_id", "bot-1",
                "secret_ciphertext", "ciphertext",
                "enabled", true);
        when(jdbc.queryForList("SELECT * FROM wecom_bot_config ORDER BY create_time LIMIT 1"))
                .thenReturn(List.of(config));
        when(secrets.resolve(null, "ciphertext")).thenReturn("secret");

        service.restoreConnection();

        verify(bridge).configure("bot-1", "secret", true);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("status='CONNECTING'"),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq("config-1"));
    }

    @Test
    void rejectsFiveFieldCronWithActionableQuartzExample() {
        when(providers.require("CUSTOM_CRON")).thenReturn(new NotificationTriggerProviderRegistry.Provider(
                "CUSTOM_CRON", "CRON", List.of("SYSTEM", "PROJECT"), List.of("customTitle")));

        MSException error = assertThrows(MSException.class, () -> service.createRule(rule(
                "CUSTOM_CRON", "CRON", "0 0/5 * * *", Map.of("userIds", List.of("user-1")), "USER",
                Map.of()), "user-1"));

        assertEquals("Invalid Quartz Cron expression: 0 0/5 * * *. Expected 6 or 7 fields, "
                + "for example: 0 0/5 * * * ?", error.getMessage());
    }

    @Test
    void testReportRequiresAnEnabledGroupBeforeGenericRecipientValidation() {
        when(providers.require("TEST_REPORT_GENERATED")).thenReturn(new NotificationTriggerProviderRegistry.Provider(
                "TEST_REPORT_GENERATED", "EVENT", List.of("SYSTEM", "PROJECT"), List.of("reportName")));

        MSException error = assertThrows(MSException.class, () -> service.createRule(rule(
                "TEST_REPORT_GENERATED", "EVENT", null, Map.of(), "CHAT",
                Map.of("generationModes", List.of("MANUAL", "AUTO"))), "user-1"));

        assertEquals("Test report notification requires at least one enabled group target", error.getMessage());
    }

    @Test
    void incompatibleBusinessRolesExplainHowToRepairTheRequest() {
        when(providers.require("CUSTOM_CRON")).thenReturn(new NotificationTriggerProviderRegistry.Provider(
                "CUSTOM_CRON", "CRON", List.of("SYSTEM", "PROJECT"), List.of("customTitle")));

        MSException error = assertThrows(MSException.class, () -> service.createRule(rule(
                "CUSTOM_CRON", "CRON", "0 0/5 * * * ?",
                Map.of("businessRoles", List.of("BUG_CREATOR")), "USER", Map.of()), "user-1"));

        assertEquals("Dynamic business recipient roles BUG_CREATOR/BUG_HANDLER are only supported by bug "
                + "expected-resolution notifications; clear businessRoles for CUSTOM_CRON", error.getMessage());
    }

    private WecomBotModels.RuleRequest rule(String notificationType, String triggerType, String cron,
                                             Map<String, Object> recipients, String deliveryMode,
                                             Map<String, Object> triggerConfig) {
        return new WecomBotModels.RuleRequest("rule", "SYSTEM", null, notificationType, triggerType,
                triggerConfig, cron, "Asia/Shanghai", "${customTitle}", recipients, deliveryMode,
                Map.of(), null, null);
    }
}
