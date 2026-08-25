package io.metersphere.system.wecombot;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.schedule.ScheduleManager;
import io.metersphere.sdk.util.LogUtils;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WecomNotificationCronScheduleService {
    private static final String GROUP = "WECOM_NOTIFICATION_RULE";
    private final ScheduleManager scheduleManager;
    private final JdbcTemplate jdbc;

    public WecomNotificationCronScheduleService(ObjectProvider<ScheduleManager> scheduleManager, JdbcTemplate jdbc) {
        this.scheduleManager = scheduleManager.getIfAvailable();
        this.jdbc = jdbc;
    }

    public void schedule(String ruleId, String cron, String timezone) {
        try {
            if (scheduleManager == null) throw new MSException("Quartz is disabled");
            JobDataMap data = new JobDataMap();
            data.put("ruleId", ruleId);
            scheduleManager.addOrUpdateCronJob(jobKey(ruleId), triggerKey(ruleId), WecomNotificationCronJob.class,
                    cron, timezone, data);
            jdbc.update("UPDATE wecom_notification_rule SET next_fire_time=? WHERE id=?",
                    scheduleManager.getNextFireTime(triggerKey(ruleId)), ruleId);
        } catch (MSException e) {
            throw e;
        } catch (Exception e) {
            throw new MSException("Invalid Cron schedule: " + e.getMessage());
        }
    }

    public record ReconcileRequest(String ruleId, Set<String> removedScheduleIds, boolean ruleDeleted) {
        public ReconcileRequest {
            removedScheduleIds = removedScheduleIds == null ? Set.of() : Set.copyOf(removedScheduleIds);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void reconcile(ReconcileRequest request) {
        request.removedScheduleIds().forEach(this::removePlan);
        if (request.ruleDeleted()) {
            remove(request.ruleId());
            return;
        }
        List<Map<String, Object>> rules = jdbc.queryForList("SELECT id,trigger_type,cron,timezone,enabled FROM wecom_notification_rule WHERE id=?", request.ruleId());
        if (rules.isEmpty()) {
            remove(request.ruleId());
            return;
        }
        Map<String, Object> rule = rules.getFirst();
        boolean enabled = Boolean.TRUE.equals(rule.get("enabled"))
                || rule.get("enabled") instanceof Number number && number.intValue() == 1;
        if (enabled && "CRON".equals(String.valueOf(rule.get("trigger_type")))) {
            schedule(request.ruleId(), String.valueOf(rule.get("cron")), String.valueOf(rule.get("timezone")));
        } else {
            remove(request.ruleId());
        }
        List<Map<String, Object>> plans = jdbc.queryForList("SELECT id,cycle_type,weekdays,execution_time,timezone,enabled FROM wecom_notification_schedule WHERE rule_id=?", request.ruleId());
        for (Map<String, Object> plan : plans) {
            String planId = String.valueOf(plan.get("id"));
            boolean planEnabled = Boolean.TRUE.equals(plan.get("enabled"))
                    || plan.get("enabled") instanceof Number number && number.intValue() == 1;
            if (enabled && planEnabled) schedulePlan(planId, cronFor(plan), String.valueOf(plan.get("timezone")));
            else removePlan(planId);
        }
    }

    public void schedulePlan(String scheduleId, String cron, String timezone) {
        try {
            if (scheduleManager == null) throw new MSException("Quartz is disabled");
            JobDataMap data = new JobDataMap();
            data.put("scheduleId", scheduleId);
            scheduleManager.addOrUpdateCronJob(planJobKey(scheduleId), planTriggerKey(scheduleId), WecomNotificationCronJob.class,
                    cron, timezone, data);
            jdbc.update("UPDATE wecom_notification_schedule SET next_fire_time=?,update_time=? WHERE id=?",
                    scheduleManager.getNextFireTime(planTriggerKey(scheduleId)), System.currentTimeMillis(), scheduleId);
        } catch (MSException e) {
            throw e;
        } catch (Exception e) {
            throw new MSException("Invalid notification schedule: " + e.getMessage());
        }
    }

    public void remove(String ruleId) {
        try {
            if (scheduleManager == null) return;
            scheduleManager.removeJob(jobKey(ruleId), triggerKey(ruleId));
            jdbc.update("UPDATE wecom_notification_rule SET next_fire_time=NULL WHERE id=?", ruleId);
        } catch (Exception e) {
            LogUtils.warn("Unable to remove WeCom notification rule Quartz job " + ruleId + ": " + e.getMessage());
        }
    }

    public void removePlan(String scheduleId) {
        try {
            if (scheduleManager != null) scheduleManager.removeJob(planJobKey(scheduleId), planTriggerKey(scheduleId));
            jdbc.update("UPDATE wecom_notification_schedule SET next_fire_time=NULL,update_time=? WHERE id=?",
                    System.currentTimeMillis(), scheduleId);
        } catch (Exception e) {
            LogUtils.warn("Unable to remove WeCom notification schedule Quartz job " + scheduleId + ": " + e.getMessage());
        }
    }

    public void recordFire(String ruleId, long fireTime) {
        try {
            Long next = scheduleManager == null ? null : scheduleManager.getNextFireTime(triggerKey(ruleId));
            jdbc.update("UPDATE wecom_notification_rule SET last_fire_time=?,next_fire_time=?,update_time=? WHERE id=?",
                    fireTime, next, System.currentTimeMillis(), ruleId);
        } catch (Exception e) {
            jdbc.update("UPDATE wecom_notification_rule SET last_fire_time=?,update_time=? WHERE id=?",
                    fireTime, System.currentTimeMillis(), ruleId);
        }
    }

    public void recordPlanFire(String scheduleId, long fireTime) {
        try {
            Long next = scheduleManager == null ? null : scheduleManager.getNextFireTime(planTriggerKey(scheduleId));
            jdbc.update("UPDATE wecom_notification_schedule SET last_fire_time=?,next_fire_time=?,update_time=? WHERE id=?",
                    fireTime, next, System.currentTimeMillis(), scheduleId);
        } catch (Exception e) {
            jdbc.update("UPDATE wecom_notification_schedule SET last_fire_time=?,update_time=? WHERE id=?",
                    fireTime, System.currentTimeMillis(), scheduleId);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        if (scheduleManager == null) return;
        List<Map<String, Object>> rules = jdbc.queryForList("SELECT id,cron,timezone FROM wecom_notification_rule WHERE enabled=1 AND trigger_type='CRON'");
        rules.forEach(rule -> safelyRestore(() -> schedule(String.valueOf(rule.get("id")), String.valueOf(rule.get("cron")), String.valueOf(rule.get("timezone")))));
        List<Map<String, Object>> plans = jdbc.queryForList("SELECT s.id,s.cycle_type,s.weekdays,s.execution_time,s.timezone FROM wecom_notification_schedule s JOIN wecom_notification_rule r ON r.id=s.rule_id WHERE s.enabled=1 AND r.enabled=1");
        plans.forEach(plan -> safelyRestore(() -> schedulePlan(String.valueOf(plan.get("id")), cronFor(plan), String.valueOf(plan.get("timezone")))));
    }

    private void safelyRestore(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LogUtils.error("Unable to restore a WeCom notification Quartz job: " + e.getMessage());
        }
    }

    public String cronFor(Map<String, Object> schedule) {
        String[] time = String.valueOf(schedule.get("execution_time")).split(":");
        if (time.length != 2) throw new MSException("Execution time must use HH:mm");
        int hour;
        int minute;
        try {
            hour = Integer.parseInt(time[0]);
            minute = Integer.parseInt(time[1]);
        } catch (NumberFormatException e) {
            throw new MSException("Execution time must use HH:mm");
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) throw new MSException("Execution time must use HH:mm");
        if ("DAILY".equals(String.valueOf(schedule.get("cycle_type")))) {
            return "0 " + minute + " " + hour + " * * ?";
        }
        String weekdays = String.valueOf(schedule.get("weekdays"));
        if (weekdays.isBlank() || "null".equalsIgnoreCase(weekdays)) throw new MSException("Weekly schedule requires weekdays");
        String names = java.util.Arrays.stream(weekdays.split(","))
                .map(String::trim).map(this::quartzWeekday).distinct().collect(java.util.stream.Collectors.joining(","));
        return "0 " + minute + " " + hour + " ? * " + names;
    }

    private String quartzWeekday(String day) {
        return switch (day) {
            case "1" -> "MON";
            case "2" -> "TUE";
            case "3" -> "WED";
            case "4" -> "THU";
            case "5" -> "FRI";
            case "6" -> "SAT";
            case "7" -> "SUN";
            default -> throw new MSException("Weekday must be between 1 and 7");
        };
    }

    private JobKey jobKey(String id) { return JobKey.jobKey("wecom-rule-" + id, GROUP); }
    private TriggerKey triggerKey(String id) { return TriggerKey.triggerKey("wecom-rule-" + id, GROUP); }
    private JobKey planJobKey(String id) { return JobKey.jobKey("wecom-schedule-" + id, GROUP); }
    private TriggerKey planTriggerKey(String id) { return TriggerKey.triggerKey("wecom-schedule-" + id, GROUP); }
}
