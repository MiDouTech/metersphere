package io.metersphere.system.wecombot;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WecomNotificationCronExecutor {
    private final WecomBotService service;

    public WecomNotificationCronExecutor(WecomBotService service) {
        this.service = service;
    }

    public void execute(String ruleId, long scheduledFireTime) {
        if (!service.isEnabled()) return;
        Map<String, Object> rule = service.rule(ruleId);
        Object enabled = rule.get("enabled");
        if (!(Boolean.TRUE.equals(enabled) || enabled instanceof Number n && n.intValue() == 1)) return;
        Long start = rule.get("start_at") instanceof Number n ? n.longValue() : null;
        Long end = rule.get("end_at") instanceof Number n ? n.longValue() : null;
        if (start != null && scheduledFireTime < start || end != null && scheduledFireTime > end) return;
        String content = service.render(String.valueOf(rule.get("template")), Map.of(
                "ruleName", String.valueOf(rule.get("name")), "now", scheduledFireTime,
                "customTitle", String.valueOf(rule.get("name")), "customContent", ""));
        try {
            service.enqueueForRule(rule, "CRON:" + ruleId + ":" + scheduledFireTime, null, null, content);
        } finally {
            service.recordCronFire(ruleId, scheduledFireTime);
        }
    }
}
