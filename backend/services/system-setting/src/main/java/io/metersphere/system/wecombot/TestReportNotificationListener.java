package io.metersphere.system.wecombot;

import io.metersphere.system.event.TestReportGeneratedEvent;
import io.metersphere.sdk.util.JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class TestReportNotificationListener {
    private final JdbcTemplate jdbc;
    private final WecomBotService botService;

    public TestReportNotificationListener(JdbcTemplate jdbc, WecomBotService botService) {
        this.jdbc = jdbc;
        this.botService = botService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGenerated(TestReportGeneratedEvent event) {
        if (!botService.isEnabled()) return;
        Long activeProject = jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id=? AND enable=1 AND deleted=0", Long.class, event.projectId());
        if (activeProject == null || activeProject == 0) return;
        List<Map<String, Object>> rules = jdbc.queryForList("SELECT * FROM wecom_notification_rule WHERE enabled=1 AND notification_type='TEST_REPORT_GENERATED' AND trigger_type='EVENT' AND (scope_type='SYSTEM' OR (scope_type='PROJECT' AND scope_id=?))",
                event.projectId());
        for (Map<String, Object> rule : rules) {
            @SuppressWarnings("unchecked")
            Map<String, Object> trigger = JSON.parseObject(String.valueOf(rule.get("trigger_config")), Map.class);
            if (trigger == null) trigger = Map.of();
            Object configuredModes = trigger.get("generationModes");
            if (configuredModes instanceof Collection<?> modes && !modes.isEmpty()
                    && modes.stream().map(String::valueOf).noneMatch(event.generationMode()::equals)) continue;
            Map<String, Object> variables = new HashMap<>();
            variables.put("reportName", event.reportName());
            variables.put("testPlanName", planName(event.testPlanId()));
            variables.put("projectName", projectName(event.projectId()));
            variables.put("reportUrl", baseUrl() + "/test-plan/testPlanReportDetail?id=" + event.reportId() + "&type=TEST_PLAN");
            variables.put("reportGeneratorName", userName(event.generatorUserId()));
            variables.put("reportSummary", reportSummary(event.reportId()));
            variables.put("generatedAt", botService.formatTimestamp(event.generatedAt(), String.valueOf(rule.get("timezone"))));
            variables.put("ruleName", rule.get("name"));
            variables.put("now", botService.formatTimestamp(event.generatedAt(), String.valueOf(rule.get("timezone"))));
            String rendered = botService.render(String.valueOf(rule.get("template")), variables);
            botService.enqueueTestReportForRule(rule, event.projectId(), event.eventId() + ":" + rule.get("id"),
                    event.reportId(), rendered);
        }
    }

    private String planName(String id) {
        List<String> values = jdbc.queryForList("SELECT name FROM test_plan WHERE id=?", String.class, id);
        return values.isEmpty() ? id : values.getFirst();
    }

    private String projectName(String id) {
        List<String> values = jdbc.queryForList("SELECT name FROM project WHERE id=?", String.class, id);
        return values.isEmpty() ? id : values.getFirst();
    }

    private String userName(String id) {
        List<String> values = jdbc.queryForList("SELECT name FROM user WHERE id=? AND enable=1 AND deleted=0", String.class, id);
        return values.isEmpty() ? id : values.getFirst();
    }

    private String reportSummary(String reportId) {
        List<String> values = jdbc.queryForList("SELECT summary FROM test_plan_report_summary WHERE test_plan_report_id=? AND summary IS NOT NULL AND summary<>'' LIMIT 1", String.class, reportId);
        return values.isEmpty() ? "-" : values.getFirst();
    }

    private String baseUrl() {
        List<String> values = jdbc.queryForList("SELECT param_value FROM system_parameter WHERE param_key='base.url' LIMIT 1", String.class);
        return values.isEmpty() ? "" : org.apache.commons.lang3.StringUtils.removeEnd(values.getFirst(), "/");
    }
}
