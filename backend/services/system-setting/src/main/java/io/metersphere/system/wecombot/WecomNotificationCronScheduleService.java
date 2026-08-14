package io.metersphere.system.wecombot;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.schedule.ScheduleManager;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.Map;

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

    public void remove(String ruleId) {
        try {
            if (scheduleManager == null) return;
            scheduleManager.removeJob(jobKey(ruleId), triggerKey(ruleId));
            jdbc.update("UPDATE wecom_notification_rule SET next_fire_time=NULL WHERE id=?", ruleId);
        } catch (Exception ignored) {
            // Deleting a rule is idempotent when its Quartz record is already absent.
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

    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        if (scheduleManager == null) return;
        List<Map<String, Object>> rules = jdbc.queryForList("SELECT id,cron,timezone FROM wecom_notification_rule WHERE enabled=1 AND trigger_type='CRON'");
        rules.forEach(rule -> schedule(String.valueOf(rule.get("id")), String.valueOf(rule.get("cron")), String.valueOf(rule.get("timezone"))));
    }

    private JobKey jobKey(String id) { return JobKey.jobKey("wecom-rule-" + id, GROUP); }
    private TriggerKey triggerKey(String id) { return TriggerKey.triggerKey("wecom-rule-" + id, GROUP); }
}
