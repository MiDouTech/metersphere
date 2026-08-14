package io.metersphere.system.wecombot;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.event.TestReportGeneratedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;

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
            Long startAt = rule.get("start_at") instanceof Number n ? n.longValue() : null;
            Long endAt = rule.get("end_at") instanceof Number n ? n.longValue() : null;
            if (startAt != null && event.generatedAt() < startAt || endAt != null && event.generatedAt() > endAt) continue;
            Map<String, Object> variables = new HashMap<>();
            variables.put("reportName", event.reportName());
            variables.put("testPlanName", planName(event.testPlanId()));
            variables.put("projectName", projectName(event.projectId()));
            variables.put("reportUrl", baseUrl() + "/test-plan/testPlanReportDetail?id=" + event.reportId() + "&type=TEST_PLAN");
            variables.put("reportGeneratorName", userName(event.generatorUserId()));
            variables.put("reportSummary", reportSummary(event.reportId()));
            variables.put("generatedAt", event.generatedAt());
            variables.put("ruleName", rule.get("name"));
            variables.put("now", event.generatedAt());
            String rendered = botService.render(String.valueOf(rule.get("template")), variables);
            String content = appendWithinLimit(rendered, projectMentions(event.projectId(), rule));
            botService.enqueueForRule(rule, event.eventId() + ":" + rule.get("id"), "TEST_REPORT", event.reportId(), content);
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

    private String appendWithinLimit(String content, String suffix) {
        if (suffix.isEmpty()) return content;
        int available = 20_480 - content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (available <= 0) return content;
        StringBuilder accepted = new StringBuilder("\n\n");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<@[^>]+>").matcher(suffix);
        while (matcher.find()) {
            String token = matcher.group() + " ";
            if ((accepted.toString() + token).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > available) break;
            accepted.append(token);
        }
        return accepted.length() == 2 ? content : content + accepted;
    }

    private String projectMentions(String projectId, Map<String, Object> rule) {
        @SuppressWarnings("unchecked")
        Map<String, Object> recipient = JSON.parseObject(String.valueOf(rule.get("recipient_spec")), Map.class);
        Object selected = recipient.get("userIds");
        List<String> userids;
        boolean explicitSelection = selected instanceof Collection<?> values && !values.isEmpty()
                || recipient.get("projectAllMembers") instanceof Boolean all && all
                || recipient.get("projectRoleIds") instanceof Collection<?> roles && !roles.isEmpty()
                || recipient.get("userGroupIds") instanceof Collection<?> groups && !groups.isEmpty();
        if (explicitSelection) {
            userids = botService.resolveRecipientUsers(rule).stream().map(userId -> {
                List<String> mapped = jdbc.queryForList("SELECT DISTINCT u.wecom_userid FROM user_role_relation urr JOIN user u ON u.id=urr.user_id WHERE urr.source_id=? AND u.id=? AND u.enable=1 AND u.deleted=0 AND u.wecom_userid IS NOT NULL AND u.wecom_userid<>''", String.class, projectId, userId);
                return mapped.isEmpty() ? null : mapped.getFirst();
            }).filter(java.util.Objects::nonNull).toList();
        } else {
            userids = jdbc.queryForList("SELECT DISTINCT u.wecom_userid FROM user_role_relation urr JOIN user u ON u.id=urr.user_id WHERE urr.source_id=? AND u.enable=1 AND u.deleted=0 AND u.wecom_userid IS NOT NULL AND u.wecom_userid<>'' LIMIT 200", String.class, projectId);
        }
        if (userids.isEmpty()) return "";
        StringBuilder mentions = new StringBuilder("\n\n");
        for (String userid : userids) {
            String next = "<@" + userid + "> ";
            if ((mentions + next).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4000) break;
            mentions.append(next);
        }
        return mentions.toString();
    }

    private String baseUrl() {
        List<String> values = jdbc.queryForList("SELECT param_value FROM system_parameter WHERE param_key='base.url' LIMIT 1", String.class);
        return values.isEmpty() ? "" : org.apache.commons.lang3.StringUtils.removeEnd(values.getFirst(), "/");
    }
}
