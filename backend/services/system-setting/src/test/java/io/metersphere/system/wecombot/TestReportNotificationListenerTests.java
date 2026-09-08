package io.metersphere.system.wecombot;

import io.metersphere.system.event.TestReportGeneratedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestReportNotificationListenerTests {

    @Test
    void functionalReportGenerationUsesTheFunctionalReportLinkAndEnqueuesTheMatchingRule() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WecomBotService botService = mock(WecomBotService.class);
        TestReportNotificationListener listener = new TestReportNotificationListener(jdbc, botService);
        Map<String, Object> rule = Map.of(
                "id", "rule-1",
                "name", "report rule",
                "trigger_config", "{\"generationModes\":[\"MANUAL\"]}",
                "timezone", "Asia/Shanghai",
                "template", "${reportUrl}");

        when(botService.isEnabled()).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(1L);
        when(jdbc.queryForList(anyString(), eq("project-1"))).thenReturn(List.of(rule));
        when(jdbc.queryForList("SELECT name FROM test_plan WHERE id=?", String.class, "plan-1"))
                .thenReturn(List.of("plan"));
        when(jdbc.queryForList("SELECT name FROM project WHERE id=?", String.class, "project-1"))
                .thenReturn(List.of("project"));
        when(jdbc.queryForList("SELECT param_value FROM system_parameter WHERE param_key='base.url' LIMIT 1", String.class))
                .thenReturn(List.of("https://ms.example"));
        when(jdbc.queryForList("SELECT name FROM user WHERE id=? AND enable=1 AND deleted=0", String.class, "user-1"))
                .thenReturn(List.of("tester"));
        when(jdbc.queryForList("SELECT name,content FROM functional_test_report WHERE id=?", "report-1"))
                .thenReturn(List.of(Map.of("name", "report", "content", "{\"conclusion\":{\"result\":\"passed\"}}")));
        when(botService.formatTimestamp(anyLong(), anyString())).thenReturn("2026-09-08 10:00");
        when(botService.render(anyString(), anyMap())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> variables = invocation.getArgument(1, Map.class);
            return String.valueOf(variables.get("reportUrl"));
        });

        listener.onGenerated(new TestReportGeneratedEvent(
                "event-1", "report-1", "plan-1", "project-1", "report", "user-1", "MANUAL",
                1_000L, TestReportGeneratedEvent.TYPE_FUNCTIONAL));

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(botService).enqueueTestReportForRule(eq(rule), eq("project-1"), eq("event-1:rule-1"),
                eq("report-1"), content.capture());
        assertTrue(content.getValue().contains("/test-plan/functionalTestReportDetail?id=report-1&mode=view"));
    }
}
