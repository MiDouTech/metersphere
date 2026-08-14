package io.metersphere.system.wecombot;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.event.BugExpectedResolutionChangedEvent;
import io.metersphere.system.uid.IDGenerator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.*;

@Service
public class BugExpectedResolutionTimerService {
    private final JdbcTemplate jdbc;
    private final WecomBotService botService;

    public BugExpectedResolutionTimerService(JdbcTemplate jdbc, WecomBotService botService) {
        this.jdbc = jdbc;
        this.botService = botService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBugChanged(BugExpectedResolutionChangedEvent event) {
        refresh(event.bugId(), event.resourceVersion());
    }

    public void refresh(String bugId, long resourceVersion) {
        jdbc.update("UPDATE wecom_notification_outbox SET status='CANCELLED',next_retry_at=NULL,lease_until=NULL,update_time=? WHERE resource_type='BUG' AND resource_id=? AND status IN ('PENDING','RETRY')",
                System.currentTimeMillis(), bugId);
        List<Map<String, Object>> bugs = jdbc.queryForList("SELECT id,project_id,expected_resolve_time,update_time,deleted FROM bug WHERE id=?", bugId);
        if (bugs.isEmpty() || activeDeadline(bugs.getFirst()) == null) {
            jdbc.update("UPDATE wecom_notification_timer SET status='CANCELLED',update_time=? WHERE resource_type='BUG' AND resource_id=? AND status IN ('WAITING','PROCESSING')",
                    System.currentTimeMillis(), bugId);
            return;
        }
        Map<String, Object> bug = bugs.getFirst();
        if (bug.get("update_time") instanceof Number version) resourceVersion = version.longValue();
        long deadline = ((Number) bug.get("expected_resolve_time")).longValue();
        long now = System.currentTimeMillis();
        if (deadline <= now) {
            jdbc.update("UPDATE wecom_notification_timer SET status='CANCELLED',update_time=? WHERE resource_type='BUG' AND resource_id=? AND status IN ('WAITING','PROCESSING')", now, bugId);
            return;
        }
        List<Map<String, Object>> rules = jdbc.queryForList("SELECT id,trigger_config,start_at,end_at FROM wecom_notification_rule WHERE enabled=1 AND notification_type='BUG_EXPECTED_RESOLUTION_DUE' AND trigger_type='DEADLINE' AND (scope_type='SYSTEM' OR (scope_type='PROJECT' AND scope_id=?))",
                bug.get("project_id"));
        Set<String> activeRules = new HashSet<>();
        for (Map<String, Object> rule : rules) {
            String ruleId = String.valueOf(rule.get("id"));
            Map<String, Object> trigger = parse(rule.get("trigger_config"));
            long leadTime = number(trigger.get("leadTime"), number(trigger.get("beforeMinutes"), 60L));
            long nextFire = deadline - durationMillis(leadTime, String.valueOf(trigger.getOrDefault("leadUnit", "MINUTE")));
            Long startAt = rule.get("start_at") instanceof Number n ? n.longValue() : null;
            Long endAt = rule.get("end_at") instanceof Number n ? n.longValue() : null;
            if (endAt != null && now > endAt || startAt != null && startAt >= deadline) {
                continue;
            }
            nextFire = Math.max(nextFire, startAt == null ? now : startAt);
            if (endAt != null && nextFire > endAt) {
                continue;
            }
            activeRules.add(ruleId);
            jdbc.update("INSERT INTO wecom_notification_timer(id,rule_id,resource_type,resource_id,resource_version,deadline_at,next_fire_at,fire_count,status,create_time,update_time) VALUES(?,?,'BUG',?,?,?,?,0,'WAITING',?,?) ON DUPLICATE KEY UPDATE resource_version=VALUES(resource_version),deadline_at=VALUES(deadline_at),next_fire_at=VALUES(next_fire_at),fire_count=0,status='WAITING',lease_until=NULL,update_time=VALUES(update_time)",
                    IDGenerator.nextStr(), ruleId, bugId, resourceVersion, deadline, nextFire, now, now);
        }
        if (activeRules.isEmpty()) {
            jdbc.update("UPDATE wecom_notification_timer SET status='CANCELLED',update_time=? WHERE resource_type='BUG' AND resource_id=?", System.currentTimeMillis(), bugId);
        } else {
            List<Map<String, Object>> existing = jdbc.queryForList("SELECT id,rule_id FROM wecom_notification_timer WHERE resource_type='BUG' AND resource_id=? AND status IN ('WAITING','PROCESSING')", bugId);
            existing.stream().filter(timer -> !activeRules.contains(String.valueOf(timer.get("rule_id"))))
                    .forEach(timer -> jdbc.update("UPDATE wecom_notification_timer SET status='CANCELLED',lease_until=NULL,update_time=? WHERE id=?",
                            System.currentTimeMillis(), timer.get("id")));
        }
    }

    @Scheduled(fixedDelayString = "${ms.wecom-bot.timer-scan-delay-ms:30000}")
    public void scan() {
        if (!botService.isEnabled()) return;
        long now = System.currentTimeMillis();
        jdbc.update("UPDATE wecom_notification_timer SET status='WAITING',lease_until=NULL,update_time=? WHERE status='PROCESSING' AND lease_until<?", now, now);
        List<Map<String, Object>> timers = jdbc.queryForList("SELECT id,rule_id,resource_id,resource_version,deadline_at,next_fire_at,fire_count FROM wecom_notification_timer WHERE status='WAITING' AND next_fire_at<=? ORDER BY next_fire_at LIMIT 100", now);
        for (Map<String, Object> timer : timers) {
            fire(timer, now);
        }
    }

    private void fire(Map<String, Object> timer, long now) {
        String timerId = String.valueOf(timer.get("id"));
        if (jdbc.update("UPDATE wecom_notification_timer SET status='PROCESSING',lease_until=?,update_time=? WHERE id=? AND status='WAITING'", now + 60_000L, now, timerId) == 0) return;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT b.id,b.num,b.title,b.status,b.handle_user,b.create_user AS bug_create_user,b.expected_resolve_time,b.project_id,b.deleted,b.update_time AS bug_update_time,r.* FROM bug b JOIN wecom_notification_rule r ON r.id=? JOIN project p ON p.id=b.project_id AND p.enable=1 AND p.deleted=0 WHERE b.id=? AND r.enabled=1",
                timer.get("rule_id"), timer.get("resource_id"));
        if (rows.isEmpty() || activeDeadline(rows.getFirst()) == null || stopped(rows.getFirst())) {
            complete(timerId, "CANCELLED", null, now);
            return;
        }
        Map<String, Object> row = rows.getFirst();
        long deadline = ((Number) row.get("expected_resolve_time")).longValue();
        Long startAt = row.get("start_at") instanceof Number n ? n.longValue() : null;
        Long endAt = row.get("end_at") instanceof Number n ? n.longValue() : null;
        if (now >= deadline || endAt != null && now > endAt) {
            complete(timerId, "CANCELLED", null, now);
            return;
        }
        if (startAt != null && now < startAt) {
            jdbc.update("UPDATE wecom_notification_timer SET status='WAITING',next_fire_at=?,lease_until=NULL,update_time=? WHERE id=?",
                    startAt, now, timerId);
            return;
        }
        long currentVersion = row.get("bug_update_time") instanceof Number n ? n.longValue() : 0L;
        if (deadline != ((Number) timer.get("deadline_at")).longValue()
                || currentVersion != ((Number) timer.get("resource_version")).longValue()) {
            refresh(String.valueOf(row.get("id")), currentVersion);
            return;
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("bugNum", row.get("num"));
        variables.put("bugTitle", row.get("title"));
        variables.put("bugStatus", row.get("status"));
        variables.put("bugHandlerNames", userNames(userIds(row.get("handle_user"))));
        variables.put("bugCreatorName", userNames(userIds(row.get("bug_create_user"))));
        variables.put("expectedResolveTime", deadline);
        variables.put("remainingTime", Math.max(0, deadline - now));
        variables.put("projectName", projectName(String.valueOf(row.get("project_id"))));
        variables.put("resourceUrl", baseUrl() + "/bug-management/detail/edit?id=" + row.get("id"));
        variables.put("ruleName", row.get("name"));
        variables.put("now", now);
        String content = botService.render(String.valueOf(row.get("template")), variables);
        String key = "BUG_DUE:" + row.get("id") + ":" + timer.get("rule_id") + ":" + timer.get("next_fire_at");
        addDefaultBugRecipients(row);
        botService.enqueueForRule(row, key, "BUG", String.valueOf(row.get("id")), content);
        Map<String, Object> trigger = parse(row.get("trigger_config"));
        long repeatInterval = number(trigger.get("repeatInterval"), number(trigger.get("repeatMinutes"), 0L));
        long interval = durationMillis(repeatInterval, String.valueOf(trigger.getOrDefault("repeatUnit", "MINUTE")));
        int maxCount = (int) number(trigger.get("maxCount"), 100L);
        int fired = ((Number) timer.get("fire_count")).intValue() + 1;
        if (interval > 0 && fired < maxCount && now + interval <= deadline) {
            jdbc.update("UPDATE wecom_notification_timer SET status='WAITING',fire_count=?,next_fire_at=?,lease_until=NULL,update_time=? WHERE id=?", fired, now + interval, now, timerId);
        } else {
            complete(timerId, "COMPLETED", fired, now);
        }
    }

    private boolean stopped(Map<String, Object> row) {
        Map<String, Object> config = parse(row.get("stop_config"));
        Object values = config.get("statuses");
        if (!(values instanceof Collection<?> collection) || collection.isEmpty()) values = parse(row.get("trigger_config")).get("terminalStatuses");
        if (!(values instanceof Collection<?> statuses)) return false;
        return statuses.stream().map(String::valueOf).anyMatch(status -> StringUtils.equals(status, String.valueOf(row.get("status"))));
    }

    private Long activeDeadline(Map<String, Object> row) {
        Object deleted = row.get("deleted");
        boolean isDeleted = Boolean.TRUE.equals(deleted) || deleted instanceof Number n && n.intValue() == 1;
        return isDeleted || !(row.get("expected_resolve_time") instanceof Number n) ? null : n.longValue();
    }

    private void complete(String id, String status, Integer count, long now) {
        jdbc.update("UPDATE wecom_notification_timer SET status=?,fire_count=COALESCE(?,fire_count),lease_until=NULL,update_time=? WHERE id=?", status, count, now, id);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(Object value) {
        return value == null ? new HashMap<>() : JSON.parseObject(String.valueOf(value), Map.class);
    }

    private long number(Object value, long fallback) {
        return value instanceof Number n ? n.longValue() : fallback;
    }

    private long durationMillis(long value, String unit) {
        return switch (unit) {
            case "DAY" -> value * 86_400_000L;
            case "HOUR" -> value * 3_600_000L;
            default -> value * 60_000L;
        };
    }

    private void addDefaultBugRecipients(Map<String, Object> rule) {
        Map<String, Object> recipient = parse(rule.get("recipient_spec"));
        Set<String> userIds = new LinkedHashSet<>();
        Object configured = recipient.get("userIds");
        if (configured instanceof Collection<?> values) values.forEach(value -> userIds.add(String.valueOf(value)));
        Set<String> businessRoles = new LinkedHashSet<>();
        Object configuredRoles = recipient.get("businessRoles");
        if (configuredRoles instanceof Collection<?> values) values.forEach(value -> businessRoles.add(String.valueOf(value)));
        if (businessRoles.isEmpty()) businessRoles.addAll(List.of("BUG_CREATOR", "BUG_HANDLER"));
        if (businessRoles.contains("BUG_CREATOR")) userIds.addAll(userIds(rule.get("bug_create_user")));
        if (businessRoles.contains("BUG_HANDLER")) {
            String handlers = String.valueOf(rule.get("handle_user"));
            try {
                Object parsed = JSON.parseObject(handlers, Object.class);
                if (parsed instanceof Collection<?> values) values.forEach(value -> userIds.add(String.valueOf(value)));
                else if (StringUtils.isNotBlank(handlers)) userIds.addAll(Arrays.asList(handlers.split(",")));
            } catch (Exception ignored) {
                if (StringUtils.isNotBlank(handlers)) userIds.addAll(Arrays.asList(handlers.split(",")));
            }
        }
        userIds.removeIf(StringUtils::isBlank);
        recipient.put("userIds", userIds);
        rule.put("recipient_spec", JSON.toJSONString(recipient));
    }

    private Set<String> userIds(Object raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null) return result;
        String value = String.valueOf(raw);
        try {
            Object parsed = JSON.parseObject(value, Object.class);
            if (parsed instanceof Collection<?> values) values.forEach(item -> result.add(String.valueOf(item)));
            else if (StringUtils.isNotBlank(value)) result.add(value);
        } catch (Exception ignored) {
            if (StringUtils.isNotBlank(value)) result.addAll(Arrays.asList(value.split(",")));
        }
        result.removeIf(item -> StringUtils.isBlank(item) || "null".equalsIgnoreCase(item));
        return result;
    }

    private String userNames(Set<String> userIds) {
        if (userIds.isEmpty()) return "-";
        String placeholders = String.join(",", Collections.nCopies(userIds.size(), "?"));
        List<String> names = jdbc.queryForList("SELECT name FROM user WHERE id IN (" + placeholders + ") AND enable=1 AND deleted=0 ORDER BY name",
                String.class, userIds.toArray());
        return names.isEmpty() ? "-" : String.join(", ", names);
    }

    private String projectName(String projectId) {
        List<String> names = jdbc.queryForList("SELECT name FROM project WHERE id=? AND deleted=0 AND enable=1", String.class, projectId);
        return names.isEmpty() ? projectId : names.getFirst();
    }

    private String baseUrl() {
        List<String> values = jdbc.queryForList("SELECT param_value FROM system_parameter WHERE param_key='base.url' LIMIT 1", String.class);
        return values.isEmpty() ? "" : StringUtils.removeEnd(values.getFirst(), "/");
    }
}
