package io.metersphere.system.wecombot;

import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import io.metersphere.system.event.BugExpectedResolutionChangedEvent;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.ZoneId;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.quartz.CronExpression;

@Service
public class WecomBotService {
    public static final Set<String> TYPES = Set.of("BUG_EXPECTED_RESOLUTION_DUE", "TEST_REPORT_GENERATED", "CUSTOM_CRON");
    public static final Set<String> TRIGGERS = Set.of("DEADLINE", "EVENT", "CRON");
    private static final Set<String> TEMPLATE_VARIABLES = Set.of(
            "bugNum", "bugTitle", "bugStatus", "bugHandlerNames", "bugCreatorName", "expectedResolveTime",
            "remainingTime", "projectName", "resourceUrl", "testPlanName", "reportName",
            "reportGeneratorName", "reportSummary", "reportUrl", "generatedAt", "now", "ruleName",
            "customTitle", "customContent");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{\\s*([a-zA-Z0-9_]+)\\s*}");

    private final JdbcTemplate jdbc;
    private final WecomSecretService secrets;
    private final WecomBotBridgeClient bridge;
    private final NotificationTriggerProviderRegistry providers;
    private final WecomNotificationCronScheduleService cronSchedules;
    private final ApplicationEventPublisher events;

    public WecomBotService(JdbcTemplate jdbc, WecomSecretService secrets, WecomBotBridgeClient bridge,
                           NotificationTriggerProviderRegistry providers, WecomNotificationCronScheduleService cronSchedules,
                           ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.secrets = secrets;
        this.bridge = bridge;
        this.providers = providers;
        this.cronSchedules = cronSchedules;
        this.events = events;
    }

    public WecomBotModels.ConfigView getConfig() {
        Map<String, Object> row = configRow();
        if (row == null) {
            return new WecomBotModels.ConfigView(null, "", "", false, false, "DISABLED", null, null, null, null);
        }
        return new WecomBotModels.ConfigView(str(row, "id"), str(row, "name"), str(row, "bot_id"),
                StringUtils.isNotBlank(str(row, "secret_ref")) || StringUtils.isNotBlank(str(row, "secret_ciphertext")),
                bool(row, "enabled"), str(row, "status"), lng(row, "last_connected_at"),
                lng(row, "last_heartbeat_at"), str(row, "last_error_code"), str(row, "last_error_message"));
    }

    @Transactional
    public WecomBotModels.ConfigView saveConfig(WecomBotModels.ConfigRequest request, String userId) {
        Map<String, Object> current = configRow();
        long now = System.currentTimeMillis();
        String secretRef = StringUtils.trimToNull(request.secretRef());
        if (secretRef != null && StringUtils.isNotBlank(request.secret())) {
            throw new MSException("Configure either a secret reference or a secret value, not both");
        }
        if (secretRef != null && !secretRef.matches("(?:env:)?[A-Z][A-Z0-9_]{2,127}")) {
            throw new MSException("Secret reference must be an environment variable name");
        }
        String encrypted = StringUtils.isNotBlank(request.secret()) ? secrets.encrypt(request.secret()) : null;
        if (current == null) {
            if (secretRef == null && encrypted == null) {
                throw new MSException("WeCom Bot secret or secret reference is required");
            }
            jdbc.update("INSERT INTO wecom_bot_config(id,organization_id,name,bot_id,secret_ref,secret_ciphertext,enabled,status,create_time,update_time,create_user,update_user) VALUES(?,?,?,?,?,?,0,'DISABLED',?,?,?,?)",
                    IDGenerator.nextStr(), null, request.name(), request.botId(), secretRef, encrypted, now, now, userId, userId);
        } else {
            String id = str(current, "id");
            if (secretRef == null && encrypted == null) {
                jdbc.update("UPDATE wecom_bot_config SET name=?,bot_id=?,update_time=?,update_user=? WHERE id=?",
                        request.name(), request.botId(), now, userId, id);
            } else {
                jdbc.update("UPDATE wecom_bot_config SET name=?,bot_id=?,secret_ref=?,secret_ciphertext=?,update_time=?,update_user=? WHERE id=?",
                        request.name(), request.botId(), secretRef, encrypted, now, userId, id);
            }
        }
        Map<String, Object> saved = requiredConfig();
        if (bool(saved, "enabled")) {
            String secret = secrets.resolve(str(saved, "secret_ref"), str(saved, "secret_ciphertext"));
            bridge.configure(str(saved, "bot_id"), secret, true);
            jdbc.update("UPDATE wecom_bot_config SET status='CONNECTING',update_time=?,update_user=? WHERE id=?",
                    System.currentTimeMillis(), userId, str(saved, "id"));
        }
        return getConfig();
    }

    @Transactional
    public WecomBotModels.StatusView enable(boolean enabled, String userId) {
        Map<String, Object> row = requiredConfig();
        long now = System.currentTimeMillis();
        if (enabled) {
            String secret = secrets.resolve(str(row, "secret_ref"), str(row, "secret_ciphertext"));
            bridge.configure(str(row, "bot_id"), secret, true);
        }
        jdbc.update("UPDATE wecom_bot_config SET enabled=?,status=?,update_time=?,update_user=? WHERE id=?",
                enabled, enabled ? "CONNECTING" : "DISABLED", now, userId, str(row, "id"));
        if (!enabled) {
            jdbc.update("UPDATE wecom_notification_outbox SET status='CANCELLED',next_retry_at=NULL,lease_until=NULL,update_time=? WHERE status IN ('PENDING','RETRY')", now);
            try {
                bridge.configure(str(row, "bot_id"), "", false);
            } catch (Exception e) {
                LogUtils.warn("WeCom Bot was disabled in MeterSphere while Bridge was unavailable");
            }
        } else {
            jdbc.queryForList("SELECT id FROM wecom_notification_rule WHERE enabled=1 AND trigger_type='DEADLINE'", String.class)
                    .forEach(this::refreshDeadlineResources);
        }
        return status();
    }

    public boolean isEnabled() {
        Map<String, Object> row = configRow();
        return row != null && bool(row, "enabled");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreConnection() {
        Map<String, Object> row = configRow();
        if (row == null || !bool(row, "enabled")) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            String secret = secrets.resolve(str(row, "secret_ref"), str(row, "secret_ciphertext"));
            bridge.configure(str(row, "bot_id"), secret, true);
            jdbc.update("UPDATE wecom_bot_config SET status='CONNECTING',last_error_code=NULL,last_error_message=NULL,update_time=? WHERE id=?",
                    now, str(row, "id"));
        } catch (Exception e) {
            jdbc.update("UPDATE wecom_bot_config SET status='OFFLINE',last_error_code='RESTORE_FAILED',last_error_message=?,update_time=? WHERE id=?",
                    safeError(e.getMessage()), now, str(row, "id"));
            LogUtils.warn("Unable to restore WeCom Bot connection after application startup");
        }
    }

    public WecomBotModels.StatusView testConnection() {
        Map<String, Object> row = requiredConfig();
        String secret = secrets.resolve(str(row, "secret_ref"), str(row, "secret_ciphertext"));
        boolean wasEnabled = bool(row, "enabled");
        bridge.configure(str(row, "bot_id"), secret, true);
        Map<String, Object> status = Map.of("state", "CONNECTING");
        try {
            for (int i = 0; i < 20; i++) {
                status = bridge.status();
                String state = Objects.toString(status.get("state"), "CONNECTING");
                if ("ONLINE".equals(state)) {
                    return new WecomBotModels.StatusView(state, true, null, System.currentTimeMillis(), null, null);
                }
                if ("AUTH_FAILED".equals(state)) throw new MSException("WeCom Bot authentication failed");
                Thread.sleep(500L);
            }
            throw new MSException("WeCom Bot connection test timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MSException("WeCom Bot connection test was interrupted");
        } finally {
            if (!wasEnabled) {
                try {
                    bridge.configure(str(row, "bot_id"), secret, false);
                } catch (Exception e) {
                    LogUtils.warn("Unable to reset Bridge after the connection test");
                }
            }
        }
    }

    public WecomBotModels.StatusView status() {
        WecomBotModels.ConfigView config = getConfig();
        long now = System.currentTimeMillis();
        boolean heartbeatFresh = config.lastHeartbeatAt() != null && now - config.lastHeartbeatAt() < 120_000;
        String state = config.enabled() && !heartbeatFresh && !"AUTH_FAILED".equals(config.status()) ? "OFFLINE" : config.status();
        return new WecomBotModels.StatusView(state, "ONLINE".equals(state) && heartbeatFresh,
                config.lastConnectedAt(), config.lastHeartbeatAt(), config.lastErrorCode(), config.lastErrorMessage());
    }

    public List<Map<String, Object>> chats() {
        return jdbc.queryForList("SELECT id,chat_id,chat_type,display_name,active,first_seen_at,last_seen_at,last_delivery_status,last_delivery_at FROM wecom_bot_chat WHERE chat_type='GROUP' ORDER BY active DESC,last_seen_at DESC");
    }

    public void renameChat(String id, String name) {
        requireUpdated(jdbc.update("UPDATE wecom_bot_chat SET display_name=?,update_time=? WHERE id=?", name, System.currentTimeMillis(), id));
    }

    public void activateChat(String id, boolean active) {
        requireUpdated(jdbc.update("UPDATE wecom_bot_chat SET active=?,update_time=? WHERE id=?", active, System.currentTimeMillis(), id));
    }

    @Transactional
    public String enqueueTest(WecomBotModels.TestMessageRequest request, boolean group, String userId) {
        requireBotEnabled();
        String targetId;
        String targetType = group ? "CHAT" : "USER";
        if (group) {
            Map<String, Object> chat = one("SELECT chat_id,active FROM wecom_bot_chat WHERE id=? AND chat_type='GROUP'", request.chatId());
            if (chat == null || !bool(chat, "active")) {
                throw new MSException("The group must be discovered and enabled before sending");
            }
            targetId = str(chat, "chat_id");
        } else {
            if (StringUtils.isBlank(request.userId())) throw new MSException("Select a test recipient");
            targetId = mappedWecomUser(request.userId());
            if (StringUtils.isBlank(targetId)) throw new MSException("Selected user has no active WeCom userid mapping");
        }
        return enqueue(null, "TEST", IDGenerator.nextStr(), "TEST:" + IDGenerator.nextStr(), targetType,
                targetId, request.content(), null, null);
    }

    public List<Map<String, Object>> rules() {
        List<Map<String, Object>> rules = jdbc.queryForList("SELECT * FROM wecom_notification_rule ORDER BY update_time DESC");
        rules.forEach(rule -> {
            List<Map<String, Object>> schedules = schedules(str(rule, "id"));
            rule.put("schedules", schedules);
            rule.put("next_fire_time", schedules.stream().map(item -> lng(item, "next_fire_time"))
                    .filter(Objects::nonNull).min(Long::compareTo).orElse(lng(rule, "next_fire_time")));
        });
        return rules;
    }

    public Map<String, Object> rule(String id) {
        Map<String, Object> row = one("SELECT * FROM wecom_notification_rule WHERE id=?", id);
        if (row == null) throw new MSException("Notification rule does not exist");
        return row;
    }

    @Transactional
    public String createRule(WecomBotModels.RuleRequest request, String userId) {
        validateRule(request, userId);
        Map<String, Object> config = requiredConfig();
        long now = System.currentTimeMillis();
        String id = IDGenerator.nextStr();
        Map<String, Object> recipients = recipientsForRule(request);
        Long startAt = usesRulePeriod(request.notificationType()) ? request.startAt() : null;
        Long endAt = usesRulePeriod(request.notificationType()) ? request.endAt() : null;
        jdbc.update("INSERT INTO wecom_notification_rule(id,name,scope_type,scope_id,bot_config_id,notification_type,trigger_type,trigger_config,cron,timezone,enabled,config_status,config_warning,message_type,template,recipient_spec,delivery_mode,stop_config,misfire_policy,start_at,end_at,version,create_time,update_time,create_user,update_user) VALUES(?,?,?,?,?,?,?,?,?,?,0,'READY',NULL,'MARKDOWN',?,?,?,?, 'DO_NOTHING',?,?,1,?,?,?,?)",
                id, request.name(), request.scopeType(), request.scopeId(), str(config, "id"), request.notificationType(),
                request.triggerType(), json(request.triggerConfig()), request.cron(), request.timezone(), request.template(),
                json(recipients), request.deliveryMode(), json(request.stopConfig()), startAt, endAt,
                now, now, userId, userId);
        syncSchedules(id, request.notificationType(), request.schedules(), userId, false);
        requestScheduleReconcile(id, Set.of(), false);
        return id;
    }

    @Transactional
    public void updateRule(String id, WecomBotModels.RuleRequest request, String userId) {
        validateRule(request, userId);
        Map<String, Object> previous = rule(id);
        Map<String, Object> recipients = recipientsForRule(request);
        Long startAt = usesRulePeriod(request.notificationType()) ? request.startAt() : null;
        Long endAt = usesRulePeriod(request.notificationType()) ? request.endAt() : null;
        requireUpdated(jdbc.update("UPDATE wecom_notification_rule SET name=?,scope_type=?,scope_id=?,notification_type=?,trigger_type=?,trigger_config=?,cron=?,timezone=?,template=?,recipient_spec=?,delivery_mode=?,stop_config=?,start_at=?,end_at=?,config_status='READY',config_warning=NULL,version=version+1,update_time=?,update_user=? WHERE id=?",
                request.name(), request.scopeType(), request.scopeId(), request.notificationType(), request.triggerType(),
                json(request.triggerConfig()), request.cron(), request.timezone(), request.template(), json(recipients),
                request.deliveryMode(), json(request.stopConfig()), startAt, endAt, System.currentTimeMillis(), userId, id));
        syncSchedules(id, request.notificationType(), request.schedules(), userId, bool(previous, "enabled"));
        jdbc.update("UPDATE wecom_notification_timer SET status='CANCELLED',update_time=? WHERE rule_id=? AND status IN ('WAITING','PROCESSING')", System.currentTimeMillis(), id);
        requestScheduleReconcile(id, Set.of(), false);
        if (bool(previous, "enabled") && "DEADLINE".equals(request.triggerType())) refreshDeadlineResources(id);
    }

    @Transactional
    public void deleteRule(String id) {
        Set<String> removedScheduleIds = schedules(id).stream().map(schedule -> str(schedule, "id")).collect(java.util.stream.Collectors.toSet());
        jdbc.update("DELETE FROM wecom_notification_schedule WHERE rule_id=?", id);
        jdbc.update("UPDATE wecom_notification_timer SET status='CANCELLED',update_time=? WHERE rule_id=?", System.currentTimeMillis(), id);
        jdbc.update("UPDATE wecom_notification_outbox SET status='CANCELLED',next_retry_at=NULL,lease_until=NULL,update_time=? WHERE rule_id=? AND status IN ('PENDING','RETRY')", System.currentTimeMillis(), id);
        requireUpdated(jdbc.update("DELETE FROM wecom_notification_rule WHERE id=?", id));
        requestScheduleReconcile(id, removedScheduleIds, true);
    }

    @Transactional
    public void enableRule(String id, boolean enabled, String userId) {
        Map<String, Object> row = rule(id);
        if (enabled) requireBotEnabled();
        Map<String, Object> recipients = parseMap(str(row, "recipient_spec"));
        if ("BUG_EXPECTED_RESOLUTION_DUE".equals(str(row, "notification_type"))
                && strings(recipients.get("chatIds")).isEmpty() && strings(recipients.get("userIds")).isEmpty()
                && strings(recipients.get("businessRoles")).isEmpty()) {
            recipients.put("businessRoles", List.of("BUG_CREATOR", "BUG_HANDLER"));
            row.put("recipient_spec", json(recipients));
            jdbc.update("UPDATE wecom_notification_rule SET recipient_spec=? WHERE id=?", json(recipients), id);
        }
        if (enabled && "NEEDS_ATTENTION".equals(str(row, "config_status"))) {
            throw new MSException(StringUtils.defaultIfBlank(str(row, "config_warning"), "Notification recipients must be completed before enabling"));
        }
        if (enabled) requireActiveRecipient(str(row, "recipient_spec"));
        if (enabled) validateDeliveryRecipients(str(row, "delivery_mode"), recipients);
        if (enabled) ensureResolvableUserRecipients(row, recipients);
        requireUpdated(jdbc.update("UPDATE wecom_notification_rule SET enabled=?,version=version+1,update_time=?,update_user=? WHERE id=?",
                enabled, System.currentTimeMillis(), userId, id));
        requestScheduleReconcile(id, Set.of(), false);
        if (!enabled) {
            jdbc.update("UPDATE wecom_notification_timer SET status='CANCELLED',update_time=? WHERE rule_id=? AND status IN ('WAITING','PROCESSING')", System.currentTimeMillis(), id);
            jdbc.update("UPDATE wecom_notification_outbox SET status='CANCELLED',next_retry_at=NULL,lease_until=NULL,update_time=? WHERE rule_id=? AND status IN ('PENDING','RETRY')", System.currentTimeMillis(), id);
        }
        if (enabled && "DEADLINE".equals(str(row, "trigger_type")) && enabledSchedules(id).isEmpty()) refreshDeadlineResources(id);
    }

    public String preview(String id, Map<String, Object> variables) {
        return render(str(rule(id), "template"), variables == null ? Map.of() : variables);
    }

    @Transactional
    public List<String> runOnce(String id, String userId, String scheduleId) {
        requireBotEnabled();
        Map<String, Object> row = rule(id);
        String notificationType = str(row, "notification_type");
        if ("TEST_REPORT_GENERATED".equals(notificationType)) {
            throw new MSException("Test report notifications can only be triggered by a generated report event");
        }
        String triggerKey = "MANUAL:" + id + ":" + IDGenerator.nextStr();
        if ("BUG_EXPECTED_RESOLUTION_DUE".equals(notificationType)) {
            List<String> ids = executeBugRule(row, triggerKey, "MANUAL", userId, scheduleId, System.currentTimeMillis());
            if (ids.isEmpty()) throw new MSException("No matching bug or valid recipient was resolved");
            return ids;
        }
        String content = render(str(row, "template"), Map.of("ruleName", str(row, "name"),
                "now", formatTimestamp(System.currentTimeMillis(), str(row, "timezone")),
                "customTitle", str(row, "name"), "customContent", ""));
        List<String> ids = enqueueForRule(row, triggerKey, null, null, content, "MANUAL", userId, scheduleId);
        if (ids.isEmpty()) throw new MSException("No valid recipient was resolved");
        return ids;
    }

    public List<String> runScheduleOnce(String scheduleId, String userId) {
        Map<String, Object> schedule = requiredSchedule(scheduleId);
        long now = System.currentTimeMillis();
        String executionId = beginScheduleExecution(schedule, "MANUAL", userId, now, 1);
        try {
            List<String> ids = runOnce(str(schedule, "rule_id"), userId, scheduleId);
            finishScheduleExecution(executionId, "SUCCESS", ids.size(), null, null, false);
            return ids;
        } catch (RuntimeException e) {
            finishScheduleExecution(executionId, "FAILED", 0, "MANUAL_EXECUTION_FAILED", e.getMessage(), false);
            throw e;
        }
    }

    public void executeSchedule(String scheduleId, long scheduledFireTime) {
        Map<String, Object> schedule = one("SELECT s.*,r.enabled rule_enabled,r.notification_type,r.name rule_name FROM wecom_notification_schedule s JOIN wecom_notification_rule r ON r.id=s.rule_id WHERE s.id=?", scheduleId);
        if (schedule == null) return;
        String executionId = beginScheduleExecution(schedule, "SCHEDULE", null, scheduledFireTime, 4);
        if (executionId == null) return;
        if (!isEnabled() || !bool(schedule, "enabled") || !bool(schedule, "rule_enabled")) {
            finishScheduleExecution(executionId, "SKIPPED", 0, "SCHEDULE_DISABLED", "Bot, rule, or schedule is disabled", false);
            cronSchedules.recordPlanFire(scheduleId, scheduledFireTime);
            return;
        }
        Map<String, Object> rule = rule(str(schedule, "rule_id"));
        String scheduleTriggerKey = "SCHEDULE:" + rule.get("id") + ":" + scheduledFireTime;
        try {
            List<String> ids = List.of();
            if ("BUG_EXPECTED_RESOLUTION_DUE".equals(str(rule, "notification_type"))) {
                ids = executeBugRule(rule, scheduleTriggerKey,
                        "SCHEDULE", null, scheduleId, scheduledFireTime);
            } else if ("CUSTOM_CRON".equals(str(rule, "notification_type"))) {
                String content = render(str(rule, "template"), Map.of("ruleName", str(rule, "name"),
                        "now", formatTimestamp(scheduledFireTime, str(rule, "timezone")), "customTitle", str(rule, "name"), "customContent", ""));
                ids = enqueueForRule(rule, scheduleTriggerKey,
                        null, null, content, "SCHEDULE", null, scheduleId);
            }
            finishScheduleExecution(executionId, "SUCCESS", ids.size(), null, null, false);
        } catch (RuntimeException e) {
            finishScheduleExecution(executionId, null, 0, "SCHEDULE_EXECUTION_FAILED", e.getMessage(), true);
            LogUtils.error("WeCom notification schedule execution failed: " + scheduleId + ", " + e.getMessage());
        } finally {
            cronSchedules.recordPlanFire(scheduleId, scheduledFireTime);
        }
    }

    private String beginScheduleExecution(Map<String, Object> schedule, String triggerMode, String triggerUserId,
                                          long plannedFireTime, int maxAttempts) {
        long now = System.currentTimeMillis();
        String id = IDGenerator.nextStr();
        try {
            jdbc.update("INSERT INTO wecom_notification_schedule_execution(id,schedule_id,rule_id,trigger_mode,trigger_user_id,planned_fire_time,actual_start_time,status,attempts,max_attempts,create_time,update_time) VALUES(?,?,?,?,?,?,?,'RUNNING',1,?,?,?)",
                    id, str(schedule, "id"), str(schedule, "rule_id"), triggerMode, triggerUserId,
                    plannedFireTime, now, maxAttempts, now, now);
            return id;
        } catch (DuplicateKeyException duplicate) {
            if (!"SCHEDULE".equals(triggerMode)) return null;
            Map<String, Object> existing = one("SELECT id,status,next_retry_at FROM wecom_notification_schedule_execution WHERE schedule_id=? AND trigger_mode=? AND planned_fire_time=?",
                    str(schedule, "id"), triggerMode, plannedFireTime);
            if (existing == null || !"RETRY".equals(str(existing, "status"))
                    || lng(existing, "next_retry_at") == null || lng(existing, "next_retry_at") > now) return null;
            int claimed = jdbc.update("UPDATE wecom_notification_schedule_execution SET status='RUNNING',attempts=attempts+1,actual_start_time=?,actual_finish_time=NULL,next_retry_at=NULL,error_code=NULL,error_message=NULL,update_time=? WHERE id=? AND status='RETRY' AND next_retry_at<=?",
                    now, now, str(existing, "id"), now);
            return claimed == 1 ? str(existing, "id") : null;
        }
    }

    private void finishScheduleExecution(String id, String requestedStatus, int targetCount, String errorCode,
                                         String errorMessage, boolean retryable) {
        if (id == null) return;
        Map<String, Object> execution = one("SELECT attempts,max_attempts FROM wecom_notification_schedule_execution WHERE id=?", id);
        if (execution == null) return;
        int attempts = ((Number) execution.get("attempts")).intValue();
        int maxAttempts = ((Number) execution.get("max_attempts")).intValue();
        boolean retry = retryable && attempts < maxAttempts;
        String status = requestedStatus == null ? (retry ? "RETRY" : "FAILED") : requestedStatus;
        long now = System.currentTimeMillis();
        long[] delays = {60_000L, 300_000L, 900_000L};
        Long nextRetryAt = retry ? now + delays[Math.min(Math.max(attempts - 1, 0), delays.length - 1)] : null;
        jdbc.update("UPDATE wecom_notification_schedule_execution SET status=?,actual_finish_time=?,next_retry_at=?,target_count=?,error_code=?,error_message=?,update_time=? WHERE id=?",
                status, now, nextRetryAt, targetCount, errorCode, safeError(errorMessage), now, id);
    }

    @Scheduled(fixedDelayString = "${ms.wecom-bot.schedule-retry-scan-delay-ms:30000}")
    public void retryScheduleExecutions() {
        long now = System.currentTimeMillis();
        jdbc.queryForList("SELECT schedule_id,planned_fire_time FROM wecom_notification_schedule_execution WHERE status='RETRY' AND next_retry_at<=? ORDER BY next_retry_at LIMIT 50", now)
                .forEach(item -> executeSchedule(str(item, "schedule_id"), ((Number) item.get("planned_fire_time")).longValue()));
    }

    public List<Map<String, Object>> scheduleExecutions(String ruleId) {
        return jdbc.queryForList("SELECT id,schedule_id,rule_id,trigger_mode,trigger_user_id,planned_fire_time,actual_start_time,actual_finish_time,status,attempts,max_attempts,next_retry_at,target_count,error_code,error_message,create_time,update_time FROM wecom_notification_schedule_execution WHERE rule_id=? ORDER BY planned_fire_time DESC LIMIT 200", ruleId);
    }

    public WecomBotModels.PageResult<Map<String, Object>> logs(int page, int pageSize, String status, String eventType,
                                                               String targetType, String ruleId, Long startAt, Long endAt) {
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        int offset = Math.max(page - 1, 0) * safeSize;
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> values = new ArrayList<>();
        if ("PARTIAL_SUCCESS".equals(status)) {
            where.append(" AND status='SUCCESS' AND partial_failure_count>0");
        } else {
            addFilter(where, values, "status", status);
        }
        addFilter(where, values, "event_type", eventType);
        addFilter(where, values, "target_type", targetType);
        addFilter(where, values, "rule_id", ruleId);
        if (startAt != null) { where.append(" AND create_time>=?"); values.add(startAt); }
        if (endAt != null) { where.append(" AND create_time<=?"); values.add(endAt); }
        List<Object> pageValues = new ArrayList<>(values);
        pageValues.add(safeSize);
        pageValues.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,rule_id,resource_type,resource_id,event_type,trigger_mode,trigger_user_id,schedule_id,target_type,target_id,recipient_user_id,payload_preview,CASE WHEN status='SUCCESS' AND partial_failure_count>0 THEN 'PARTIAL_SUCCESS' ELSE status END status,status actual_status,attempts,max_attempts,next_retry_at,error_code,error_message,retryable,partial_failure_count,partial_failure_detail,create_time,update_time,sent_at FROM wecom_notification_outbox" + where + " ORDER BY create_time DESC LIMIT ? OFFSET ?", pageValues.toArray());
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM wecom_notification_outbox" + where, Long.class, values.toArray());
        return new WecomBotModels.PageResult<>(rows, total == null ? 0 : total);
    }

    private void addFilter(StringBuilder where, List<Object> values, String column, String value) {
        if (StringUtils.isNotBlank(value)) { where.append(" AND ").append(column).append("=?"); values.add(value); }
    }

    public Map<String, Object> log(String id) {
        Map<String, Object> row = one("SELECT id,rule_id,resource_type,resource_id,event_type,trigger_mode,trigger_user_id,schedule_id,target_type,target_id,recipient_user_id,configured_recipient_spec,resolved_user_ids,mentioned_user_ids,partial_failure_count,partial_failure_detail,payload_preview,payload_hash,CASE WHEN status='SUCCESS' AND partial_failure_count>0 THEN 'PARTIAL_SUCCESS' ELSE status END status,status actual_status,attempts,max_attempts,next_retry_at,request_id,error_code,error_message,create_time,update_time,sent_at FROM wecom_notification_outbox WHERE id=?", id);
        if (row == null) throw new MSException("Notification message does not exist");
        return row;
    }

    public Map<String, Object> metrics() {
        long now = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("botStatus", status());
        result.put("outboxByStatus", jdbc.queryForList("SELECT status,COUNT(*) count FROM wecom_notification_outbox GROUP BY status"));
        result.put("oldestPendingAgeMs", Optional.ofNullable(jdbc.queryForObject("SELECT MIN(create_time) FROM wecom_notification_outbox WHERE status IN ('PENDING','RETRY','SENDING')", Long.class)).map(value -> now - value).orElse(0L));
        result.put("dueTimerCount", jdbc.queryForObject("SELECT COUNT(*) FROM wecom_notification_timer WHERE status='WAITING' AND next_fire_at<=?", Long.class, now));
        result.put("discoveredGroupCount", jdbc.queryForObject("SELECT COUNT(*) FROM wecom_bot_chat WHERE chat_type='GROUP'", Long.class));
        return result;
    }

    public void retry(String id) {
        requireUpdated(jdbc.update("UPDATE wecom_notification_outbox SET status='RETRY',next_retry_at=?,lease_until=NULL,error_code=NULL,error_message=NULL,update_time=? WHERE id=? AND status='FAILED' AND retryable=1",
                System.currentTimeMillis(), System.currentTimeMillis(), id));
    }

    @Transactional
    public void callback(WecomBotModels.CallbackEvent event, String eventType, String nonce) {
        long now = System.currentTimeMillis();
        try {
            jdbc.update("INSERT INTO wecom_bot_callback_event(id,nonce,event_type,occurred_at,create_time,update_time) VALUES(?,?,?,?,?,?)",
                    event.eventId(), nonce, eventType, event.occurredAt() == null ? now : event.occurredAt(), now, now);
        } catch (DuplicateKeyException e) {
            return;
        }
        if ("STATUS".equals(eventType)) {
            jdbc.update("UPDATE wecom_bot_config SET status=?,last_connected_at=CASE WHEN ?='ONLINE' THEN ? ELSE last_connected_at END,last_heartbeat_at=?,last_error_code=?,last_error_message=?,update_time=? WHERE bot_id=?",
                    event.status(), event.status(), now, now, event.errorCode(), safeError(event.errorMessage()), now, event.botId());
        } else if ("CHAT".equals(eventType)) {
            Map<String, Object> config = requiredConfig();
            String type = "group".equalsIgnoreCase(event.chatType()) ? "GROUP" : "SINGLE";
            if (StringUtils.isNotBlank(event.chatId())) {
                jdbc.update("INSERT INTO wecom_bot_chat(id,bot_config_id,chat_id,chat_type,display_name,source_userid,active,first_seen_at,last_seen_at,metadata,create_time,update_time) VALUES(?,?,?,?,?,?,0,?,?,?,?,?) ON DUPLICATE KEY UPDATE last_seen_at=VALUES(last_seen_at),source_userid=VALUES(source_userid),metadata=VALUES(metadata),update_time=VALUES(update_time)",
                        IDGenerator.nextStr(), str(config, "id"), event.chatId(), type, event.displayName(), event.fromUserid(), now, now,
                        json(event.metadata()), now, now);
            }
        } else if ("DELIVERY".equals(eventType) && StringUtils.isNotBlank(event.outboxId())) {
            finishDelivery(event.outboxId(), Boolean.TRUE.equals(event.success()), Boolean.TRUE.equals(event.retryable()),
                    event.errorCode(), event.errorMessage());
        }
        jdbc.update("DELETE FROM wecom_bot_callback_event WHERE create_time<?", now - 86_400_000L);
    }

    @Transactional
    public String enqueue(String ruleId, String eventType, String eventId, String triggerKey, String targetType,
                          String targetId, String content, String resourceType, String resourceId) {
        return enqueue(ruleId, eventType, eventId, triggerKey, targetType, targetId, content, resourceType, resourceId,
                null, null, null);
    }

    public String enqueue(String ruleId, String eventType, String eventId, String triggerKey, String targetType,
                          String targetId, String content, String resourceType, String resourceId,
                          String triggerMode, String triggerUserId, String scheduleId) {
        if (StringUtils.isBlank(content) || content.getBytes(StandardCharsets.UTF_8).length > 20_480) {
            throw new MSException("Message content must be between 1 and 20480 bytes");
        }
        String id = IDGenerator.nextStr();
        long now = System.currentTimeMillis();
        String payload = JSON.toJSONString(Map.of("content", content));
        try {
            jdbc.update("INSERT INTO wecom_notification_outbox(id,rule_id,resource_type,resource_id,event_type,event_id,trigger_mode,trigger_user_id,schedule_id,trigger_key,target_type,target_id,message_type,payload,payload_preview,payload_hash,status,attempts,max_attempts,next_retry_at,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'MARKDOWN',?,?,?,'PENDING',0,4,?,?,?)",
                    id, ruleId, resourceType, resourceId, eventType, eventId, triggerMode, triggerUserId, scheduleId,
                    triggerKey, targetType, targetId, payload,
                    preview(content), DigestUtils.sha256Hex(payload), now, now, now);
            return id;
        } catch (DuplicateKeyException e) {
            return jdbc.queryForObject("SELECT id FROM wecom_notification_outbox WHERE trigger_key=? AND target_type=? AND target_id=?", String.class, triggerKey, targetType, targetId);
        }
    }

    public List<String> enqueueForRule(Map<String, Object> rule, String triggerKey, String resourceType,
                                       String resourceId, String content) {
        return enqueueForRule(rule, triggerKey, resourceType, resourceId, content, null, null, null);
    }

    public List<String> enqueueForRule(Map<String, Object> rule, String triggerKey, String resourceType,
                                       String resourceId, String content, String triggerMode,
                                       String triggerUserId, String scheduleId) {
        if (!isEnabled()) return List.of();
        Map<String, Object> recipient = parseMap(str(rule, "recipient_spec"));
        List<String> result = new ArrayList<>();
        String deliveryMode = str(rule, "delivery_mode");
        boolean sendChats = "CHAT".equals(deliveryMode) || "BOTH".equals(deliveryMode);
        boolean reportNotification = "TEST_REPORT_GENERATED".equals(str(rule, "notification_type"));
        boolean sendUsers = !reportNotification && ("USER".equals(deliveryMode) || "BOTH".equals(deliveryMode));
        if (sendChats) {
            for (String chatId : strings(recipient.get("chatIds"))) {
                String outboxId = enqueue(str(rule, "id"), str(rule, "notification_type"), triggerKey,
                        triggerKey, "CHAT", chatId, content, resourceType, resourceId, triggerMode, triggerUserId, scheduleId);
                result.add(outboxId);
                if (!activeChat(chatId)) finishDelivery(outboxId, false, false, "INACTIVE_CHAT", "Recipient group is not discovered or enabled");
            }
        }
        if (sendUsers) {
            Set<String> resolvedUsers = resolveRecipientUsers(rule, recipient);
            if (resolvedUsers.isEmpty()) {
                String failedId = enqueue(str(rule, "id"), str(rule, "notification_type"), triggerKey,
                        triggerKey, "USER", "UNRESOLVED:" + DigestUtils.sha256Hex(str(rule, "id")).substring(0, 16),
                        content, resourceType, resourceId, triggerMode, triggerUserId, scheduleId);
                finishDelivery(failedId, false, false, "NO_ACTIVE_RECIPIENT",
                        "Configured users, positions, and roles currently resolve to no active recipient");
                result.add(failedId);
            }
            for (String userId : resolvedUsers) {
                String wecomId = mappedWecomUser(userId);
                if (wecomId != null) {
                    result.add(enqueue(str(rule, "id"), str(rule, "notification_type"), triggerKey,
                            triggerKey, "USER", wecomId, content, resourceType, resourceId, triggerMode, triggerUserId, scheduleId));
                } else {
                    String failedId = enqueue(str(rule, "id"), str(rule, "notification_type"), triggerKey,
                            triggerKey, "USER", "UNMAPPED:" + DigestUtils.sha256Hex(userId).substring(0, 16), content,
                            resourceType, resourceId, triggerMode, triggerUserId, scheduleId);
                    finishDelivery(failedId, false, false, "MISSING_WECOM_USERID", "Recipient has no active WeCom userid mapping");
                    result.add(failedId);
                }
            }
        }
        return result;
    }

    public String render(String template, Map<String, Object> variables) {
        validateTemplate(template);
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            matcher.appendReplacement(output, Matcher.quoteReplacement(value == null ? "-" : String.valueOf(value)));
        }
        matcher.appendTail(output);
        String result = output.toString();
        if (result.getBytes(StandardCharsets.UTF_8).length > 20_480) throw new MSException("Rendered message is too long");
        return result;
    }

    public void finishDelivery(String id, boolean success, boolean retryable, String code, String message) {
        long now = System.currentTimeMillis();
        Map<String, Object> row = one("SELECT status,attempts,max_attempts,target_type,target_id FROM wecom_notification_outbox WHERE id=?", id);
        if (row == null) return;
        if ("SUCCESS".equals(str(row, "status"))) return;
        int attempts = ((Number) row.get("attempts")).intValue();
        int max = ((Number) row.get("max_attempts")).intValue();
        String state = success ? "SUCCESS" : (retryable && attempts < max ? "RETRY" : "FAILED");
        long[] delays = {60_000L, 300_000L, 900_000L};
        Long retryAt = "RETRY".equals(state) ? now + delays[Math.min(Math.max(attempts - 1, 0), delays.length - 1)] : null;
        jdbc.update("UPDATE wecom_notification_outbox SET status=?,next_retry_at=?,lease_until=NULL,error_code=?,error_message=?,retryable=?,sent_at=?,update_time=? WHERE id=?",
                state, retryAt, code, safeError(message), retryable, success ? now : null, now, id);
        if ("CHAT".equals(str(row, "target_type"))) {
            jdbc.update("UPDATE wecom_bot_chat SET last_delivery_status=?,last_delivery_at=?,update_time=? WHERE chat_id=?",
                    state, now, now, str(row, "target_id"));
        }
    }

    private void validateRule(WecomBotModels.RuleRequest request, String userId) {
        NotificationTriggerProviderRegistry.Provider provider = providers.require(request.notificationType());
        if (!TYPES.contains(request.notificationType()) || !TRIGGERS.contains(request.triggerType())) {
            throw new MSException("Unsupported notification or trigger type");
        }
        if (!provider.triggerType().equals(request.triggerType()) || !provider.scopes().contains(request.scopeType())) {
            throw new MSException("Notification trigger or scope is not supported by the provider");
        }
        if ("PROJECT".equals(request.scopeType())) {
            if (StringUtils.isBlank(request.scopeId())) throw new MSException("Project scope requires a project");
            Long project = jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id=? AND enable=1 AND deleted=0", Long.class, request.scopeId());
            Long owner = jdbc.queryForObject("SELECT COUNT(*) FROM user_role_relation WHERE user_id=? AND ((source_id=? AND role_id='project_admin') OR role_id='admin')", Long.class, userId, request.scopeId());
            if (project == null || project == 0 || owner == null || owner == 0) {
                throw new MSException("Current user cannot manage notification rules for this project");
            }
        }
        if ("BUG_EXPECTED_RESOLUTION_DUE".equals(request.notificationType()) && !"DEADLINE".equals(request.triggerType())
                || "TEST_REPORT_GENERATED".equals(request.notificationType()) && !"EVENT".equals(request.triggerType())
                || "CUSTOM_CRON".equals(request.notificationType()) && !"CRON".equals(request.triggerType())) {
            throw new MSException("Notification type and trigger type do not match");
        }
        if ("CRON".equals(request.triggerType()) && (StringUtils.isBlank(request.cron()) || StringUtils.isBlank(request.timezone()))) {
            throw new MSException("Cron and timezone are required");
        }
        if (!Set.of("USER", "CHAT", "BOTH").contains(request.deliveryMode())) {
            throw new MSException("Unsupported delivery mode");
        }
        if ("CRON".equals(request.triggerType()) && !CronExpression.isValidExpression(request.cron())) {
            throw new MSException("Invalid Quartz Cron expression: " + request.cron()
                    + ". Expected 6 or 7 fields, for example: 0 0/5 * * * ?");
        }
        try {
            ZoneId.of(request.timezone());
        } catch (Exception e) {
            throw new MSException("Invalid timezone");
        }
        if (usesRulePeriod(request.notificationType()) && request.startAt() != null && request.endAt() != null && request.startAt() >= request.endAt()) {
            throw new MSException("Rule end time must be later than start time");
        }
        if ("DEADLINE".equals(request.triggerType())) {
            Object lead = request.triggerConfig() == null ? null : request.triggerConfig().get("leadTime");
            Object repeat = request.triggerConfig() == null ? null : request.triggerConfig().get("repeatInterval");
            Object maxCount = request.triggerConfig() == null ? null : request.triggerConfig().get("maxCount");
            String leadUnit = Objects.toString(request.triggerConfig() == null ? null : request.triggerConfig().get("leadUnit"), "MINUTE");
            String repeatUnit = Objects.toString(request.triggerConfig() == null ? null : request.triggerConfig().get("repeatUnit"), "MINUTE");
            if (!(lead instanceof Number leadNumber) || !(repeat instanceof Number repeatNumber)
                    || leadNumber.longValue() < 0 || repeatNumber.longValue() < 0
                    || leadNumber.longValue() > 525_600 || repeatNumber.longValue() > 525_600
                    || !Set.of("MINUTE", "HOUR", "DAY").contains(leadUnit)
                    || !Set.of("MINUTE", "HOUR", "DAY").contains(repeatUnit)
                    || !(maxCount instanceof Number count) || count.intValue() < 1 || count.intValue() > 100) {
                throw new MSException("Invalid deadline reminder interval, unit, or maximum count");
            }
            validateTerminalStatuses(request.stopConfig(), request.triggerConfig());
        }
        if ("TEST_REPORT_GENERATED".equals(request.notificationType())) {
            Object modes = request.triggerConfig() == null ? null : request.triggerConfig().get("generationModes");
            if (!(modes instanceof Collection<?> values) || values.isEmpty()
                    || values.stream().map(String::valueOf).anyMatch(value -> !Set.of("MANUAL", "AUTO").contains(value))) {
                throw new MSException("At least one valid report generation source is required");
            }
        }
        validateTemplate(request.template(), request.notificationType());
        Map<String, Object> recipients = recipientsForRule(request);
        if ("TEST_REPORT_GENERATED".equals(request.notificationType())
                && strings(recipients.get("chatIds")).isEmpty()) {
            throw new MSException("Test report notification requires at least one enabled group target");
        }
        requireActiveRecipient(json(recipients));
        validateDeliveryRecipients(request.deliveryMode(), recipients);
        validateRecipientSelectors(request, recipients);
        boolean userDelivery = "USER".equals(request.deliveryMode()) || "BOTH".equals(request.deliveryMode());
        boolean resolvableSelectors = !strings(recipients.get("userIds")).isEmpty()
                || Boolean.TRUE.equals(recipients.get("projectAllMembers"))
                || !strings(recipients.get("positionIds")).isEmpty()
                || !strings(recipients.get("roleIds")).isEmpty();
        if (userDelivery && resolvableSelectors && strings(recipients.get("businessRoles")).isEmpty()) {
            Map<String, Object> previewRule = new HashMap<>();
            previewRule.put("scope_type", request.scopeType());
            previewRule.put("scope_id", request.scopeId());
            if (resolveRecipientUsers(previewRule, recipients).isEmpty()) {
                throw new MSException("The selected users, positions, and roles currently resolve to no active recipient");
            }
        }
        validateSchedules(request.notificationType(), request.schedules(), null, null);
        if ("BUG_EXPECTED_RESOLUTION_DUE".equals(request.notificationType())
                && strings(recipients.get("chatIds")).isEmpty() && strings(recipients.get("userIds")).isEmpty()
                && !List.of("USER", "BOTH").contains(request.deliveryMode())) {
            throw new MSException("Default bug creator and handler recipients require personal delivery");
        }
        if ("TEST_REPORT_GENERATED".equals(request.notificationType()) && "USER".equals(request.deliveryMode())) {
            throw new MSException("Test report notification must be delivered to a group");
        }
    }

    private void validateTemplate(String template) {
        if (StringUtils.isBlank(template)) throw new MSException("Message template is required");
        if (template.getBytes(StandardCharsets.UTF_8).length > 20_480) throw new MSException("Message template is too long");
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            if (!TEMPLATE_VARIABLES.contains(matcher.group(1))) throw new MSException("Unsupported template variable: " + matcher.group(1));
        }
    }

    private void requireActiveRecipient(String recipientJson) {
        Map<String, Object> recipient = parseMap(recipientJson);
        List<String> chatIds = strings(recipient.get("chatIds"));
        List<String> userIds = strings(recipient.get("userIds"));
        boolean dynamic = Boolean.TRUE.equals(recipient.get("projectAllMembers"))
                || !strings(recipient.get("positionIds")).isEmpty()
                || !strings(recipient.get("roleIds")).isEmpty()
                || !strings(recipient.get("businessRoles")).isEmpty();
        if (chatIds.isEmpty() && userIds.isEmpty() && !dynamic) throw new MSException("At least one recipient is required");
        for (String chatId : chatIds) if (!activeChat(chatId)) throw new MSException("Recipient group is not discovered or enabled");
    }

    private void validateDeliveryRecipients(String deliveryMode, Map<String, Object> recipient) {
        boolean chats = !strings(recipient.get("chatIds")).isEmpty();
        boolean users = !strings(recipient.get("userIds")).isEmpty()
                || Boolean.TRUE.equals(recipient.get("projectAllMembers"))
                || !strings(recipient.get("positionIds")).isEmpty()
                || !strings(recipient.get("roleIds")).isEmpty()
                || !strings(recipient.get("businessRoles")).isEmpty();
        if ("CHAT".equals(deliveryMode) && !chats || "USER".equals(deliveryMode) && !users
                || "BOTH".equals(deliveryMode) && !chats && !users) {
            throw new MSException("The delivery mode has no matching recipient");
        }
    }

    private void validateRecipientSelectors(WecomBotModels.RuleRequest request, Map<String, Object> recipient) {
        List<String> businessRoles = strings(recipient.get("businessRoles"));
        if (!businessRoles.isEmpty() && (!"BUG_EXPECTED_RESOLUTION_DUE".equals(request.notificationType())
                || businessRoles.stream().anyMatch(value -> !Set.of("BUG_CREATOR", "BUG_HANDLER").contains(value)))) {
            throw new MSException("Dynamic business recipient roles BUG_CREATOR/BUG_HANDLER are only supported "
                    + "by bug expected-resolution notifications; clear businessRoles for " + request.notificationType());
        }
        Set<String> roleIds = new LinkedHashSet<>(strings(recipient.get("roleIds")));
        if (!roleIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(roleIds.size(), "?"));
            List<Object> args = new ArrayList<>(roleIds);
            String sql = "SELECT COUNT(*) FROM user_role WHERE enabled=1 AND id IN (" + placeholders + ")";
            if ("PROJECT".equals(request.scopeType())) {
                sql += " AND (type='SYSTEM' OR (type='PROJECT' AND (scope_id='global' OR scope_id=?)) OR (type='ORGANIZATION' AND (scope_id='global' OR scope_id=(SELECT organization_id FROM project WHERE id=?))))";
                args.add(request.scopeId());
                args.add(request.scopeId());
            } else {
                sql += " AND type<>'PROJECT'";
            }
            Long count = jdbc.queryForObject(sql, Long.class, args.toArray());
            if (count == null || count != roleIds.size()) throw new MSException("One or more recipient roles are invalid for the selected scope");
        }
        if (!strings(recipient.get("positionIds")).isEmpty()) refreshPositionCatalog();
        for (String positionId : strings(recipient.get("positionIds"))) {
            String positionSql = "SELECT COUNT(DISTINCT u.id) FROM user u";
            List<Object> positionArgs = new ArrayList<>();
            if ("PROJECT".equals(request.scopeType())) {
                positionSql += " JOIN user_role_relation urr ON urr.user_id=u.id AND urr.source_id=?";
                positionArgs.add(request.scopeId());
            }
            positionSql += " JOIN wecom_recipient_position rp ON LOWER(TRIM(u.position))=rp.normalized_name WHERE u.enable=1 AND u.deleted=0 AND rp.id=?";
            positionArgs.add(positionId);
            Long positions = jdbc.queryForObject(positionSql, Long.class, positionArgs.toArray());
            if (positions == null || positions == 0) throw new MSException("One or more recipient positions no longer exist");
        }
    }

    private boolean activeChat(String chatId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM wecom_bot_chat WHERE chat_id=? AND chat_type='GROUP' AND active=1", Long.class, chatId);
        return count != null && count > 0;
    }

    private String mappedWecomUser(String userId) {
        try {
            return jdbc.queryForObject("SELECT wecom_userid FROM user WHERE id=? AND enable=1 AND deleted=0 AND wecom_userid IS NOT NULL AND wecom_userid<>''", String.class, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<Map<String, Object>> userOptions(String projectId) {
        if (StringUtils.isBlank(projectId)) {
            return jdbc.queryForList("SELECT id,name,(wecom_userid IS NOT NULL AND wecom_userid<>'') mapped,false projectMember FROM user WHERE enable=1 AND deleted=0 ORDER BY name LIMIT 1000");
        }
        return jdbc.queryForList("SELECT u.id,u.name,(u.wecom_userid IS NOT NULL AND u.wecom_userid<>'') mapped,EXISTS(SELECT 1 FROM user_role_relation urr WHERE urr.source_id=? AND urr.user_id=u.id) projectMember FROM user u WHERE u.enable=1 AND u.deleted=0 ORDER BY projectMember DESC,u.name LIMIT 1000", projectId);
    }

    private void ensureResolvableUserRecipients(Map<String, Object> rule, Map<String, Object> recipient) {
        boolean userDelivery = "USER".equals(str(rule, "delivery_mode")) || "BOTH".equals(str(rule, "delivery_mode"));
        if (!userDelivery || !strings(recipient.get("businessRoles")).isEmpty()) return;
        if (resolveRecipientUsers(rule, recipient).isEmpty()) {
            throw new MSException("The configured users, positions, and roles currently resolve to no active recipient");
        }
    }

    private void validateTemplate(String template, String notificationType) {
        validateTemplate(template);
        Set<String> supported = new HashSet<>(providers.require(notificationType).variables());
        supported.add("ruleName");
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            if (!supported.contains(matcher.group(1))) {
                throw new MSException("Template variable is not supported by this notification type: " + matcher.group(1));
            }
        }
    }

    public List<Map<String, Object>> roleOptions(String projectId) {
        if (StringUtils.isBlank(projectId)) {
            return jdbc.queryForList("SELECT id,name,type,scope_id FROM user_role WHERE enabled=1 AND type<>'PROJECT' ORDER BY type,name LIMIT 1000");
        }
        return jdbc.queryForList("SELECT id,name,type,scope_id FROM user_role WHERE enabled=1 AND (type='SYSTEM' OR (type='PROJECT' AND (scope_id='global' OR scope_id=?)) OR (type='ORGANIZATION' AND (scope_id='global' OR scope_id=(SELECT organization_id FROM project WHERE id=?)))) ORDER BY type,name LIMIT 1000", projectId, projectId);
    }

    public List<Map<String, Object>> positionOptions(String projectId) {
        refreshPositionCatalog();
        if (StringUtils.isBlank(projectId)) {
            return jdbc.queryForList("SELECT p.id,p.display_name name,COUNT(*) memberCount FROM wecom_recipient_position p JOIN user u ON LOWER(TRIM(u.position))=p.normalized_name WHERE u.enable=1 AND u.deleted=0 GROUP BY p.id,p.display_name ORDER BY name LIMIT 1000");
        }
        return jdbc.queryForList("SELECT p.id,p.display_name name,COUNT(DISTINCT u.id) memberCount FROM wecom_recipient_position p JOIN user u ON LOWER(TRIM(u.position))=p.normalized_name JOIN user_role_relation urr ON urr.user_id=u.id AND urr.source_id=? WHERE u.enable=1 AND u.deleted=0 GROUP BY p.id,p.display_name ORDER BY name LIMIT 1000", projectId);
    }

    private void refreshPositionCatalog() {
        long now = System.currentTimeMillis();
        jdbc.update("INSERT IGNORE INTO wecom_recipient_position(id,normalized_name,display_name,create_time,update_time) "
                        + "SELECT UUID_SHORT(),LOWER(TRIM(position)),MIN(TRIM(position)),?,? FROM user "
                        + "WHERE enable=1 AND deleted=0 AND position IS NOT NULL AND TRIM(position)<>'' GROUP BY LOWER(TRIM(position))",
                now, now);
    }

    public List<Map<String, Object>> bugTerminalStatuses() {
        return jdbc.queryForList("SELECT si.id,si.name,si.status_code FROM status_item si JOIN workflow_definition wd ON wd.id=si.flow_id WHERE wd.scene='BUG' AND wd.lifecycle='PUBLISHED' AND wd.active_for_new=1 AND wd.enabled=1 AND si.enabled=1 AND si.terminal_status=1 ORDER BY si.pos");
    }

    private Set<String> resolveRecipientUsers(Map<String, Object> rule, Map<String, Object> recipient) {
        Set<String> users = new LinkedHashSet<>(strings(recipient.get("userIds")));
        String projectId = str(rule, "scope_id");
        if (Boolean.TRUE.equals(recipient.get("projectAllMembers")) && StringUtils.isNotBlank(projectId)) {
            users.addAll(jdbc.queryForList("SELECT DISTINCT user_id FROM user_role_relation WHERE source_id=?", String.class, projectId));
        }
        Set<String> roleIds = new LinkedHashSet<>(strings(recipient.get("roleIds")));
        if (!roleIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(roleIds.size(), "?"));
            List<Object> args = new ArrayList<>(roleIds);
            String sql = "SELECT DISTINCT urr.user_id FROM user_role_relation urr JOIN user_role ur ON ur.id=urr.role_id AND ur.enabled=1 WHERE urr.role_id IN (" + placeholders + ")";
            if (StringUtils.isNotBlank(projectId)) {
                sql += " AND (ur.type='SYSTEM' OR (ur.type='PROJECT' AND urr.source_id=?) OR (ur.type='ORGANIZATION' AND (urr.organization_id=(SELECT organization_id FROM project WHERE id=?) OR urr.source_id=(SELECT organization_id FROM project WHERE id=?))))";
                args.add(projectId);
                args.add(projectId);
                args.add(projectId);
            }
            users.addAll(jdbc.queryForList(sql, String.class, args.toArray()));
        }
        List<String> positionIds = strings(recipient.get("positionIds"));
        if (!positionIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(positionIds.size(), "?"));
            List<Object> args = new ArrayList<>(positionIds);
            String sql = "SELECT DISTINCT u.id FROM user u JOIN wecom_recipient_position rp ON LOWER(TRIM(u.position))=rp.normalized_name WHERE u.enable=1 AND u.deleted=0 AND rp.id IN (" + placeholders + ")";
            if (StringUtils.isNotBlank(projectId)) {
                sql += " AND EXISTS(SELECT 1 FROM user_role_relation urr WHERE urr.user_id=u.id AND urr.source_id=?)";
                args.add(projectId);
            }
            users.addAll(jdbc.queryForList(sql, String.class, args.toArray()));
        }
        if (!users.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(users.size(), "?"));
            users.retainAll(jdbc.queryForList("SELECT id FROM user WHERE enable=1 AND deleted=0 AND id IN (" + placeholders + ")",
                    String.class, users.toArray()));
        }
        return users;
    }

    Set<String> resolveRecipientUsers(Map<String, Object> rule) {
        return resolveRecipientUsers(rule, parseMap(str(rule, "recipient_spec")));
    }

    public List<String> enqueueTestReportForRule(Map<String, Object> rule, String projectId, String triggerKey,
                                                  String reportId, String content) {
        if (!isEnabled()) return List.of();
        Map<String, Object> recipient = parseMap(str(rule, "recipient_spec"));
        Set<String> users = new LinkedHashSet<>(resolveRecipientUsers(rule, recipient));
        if (strings(recipient.get("userIds")).isEmpty()) {
            users.addAll(jdbc.queryForList("SELECT DISTINCT u.id FROM user_role_relation urr JOIN user u ON u.id=urr.user_id WHERE urr.source_id=? AND u.enable=1 AND u.deleted=0",
                    String.class, projectId));
        }
        Map<String, String> mapped = mappedWecomUsers(users);
        MentionResult mentions = appendMentions(content, mapped);
        String mentionedContent = mentions.content();
        Map<String, String> userNames = userNamesById(users);
        List<Map<String, Object>> mentionFailures = new ArrayList<>();
        for (String userId : users) {
            if (mapped.containsKey(userId) && mentions.includedUserIds().contains(userId)) continue;
            mentionFailures.add(Map.of(
                    "userId", userId,
                    "userName", userNames.getOrDefault(userId, userId),
                    "reason", mapped.containsKey(userId) ? "MENTION_LIMIT_EXCEEDED" : "MISSING_WECOM_USERID"));
        }
        List<String> result = new ArrayList<>();
        for (String chatId : strings(recipient.get("chatIds"))) {
            String outboxId = enqueue(str(rule, "id"), str(rule, "notification_type"), triggerKey,
                    triggerKey, "CHAT", chatId, mentionedContent, "TEST_REPORT", reportId, "EVENT", null, null);
            result.add(outboxId);
            jdbc.update("UPDATE wecom_notification_outbox SET configured_recipient_spec=?,resolved_user_ids=?,mentioned_user_ids=?,partial_failure_count=?,partial_failure_detail=?,update_time=? WHERE id=?",
                    json(recipient), json(users), json(mentions.includedUserIds()), mentionFailures.size(),
                    mentionFailures.isEmpty() ? null : json(mentionFailures), System.currentTimeMillis(), outboxId);
            if (!activeChat(chatId)) finishDelivery(outboxId, false, false, "INACTIVE_CHAT", "Recipient group is not discovered or enabled");
        }
        for (String userId : users) {
            if (mapped.containsKey(userId) && mentions.includedUserIds().contains(userId)) continue;
            boolean mappedButOmitted = mapped.containsKey(userId);
            String failedId = enqueue(str(rule, "id"), str(rule, "notification_type"), triggerKey,
                    triggerKey, "MENTION", (mappedButOmitted ? "OMITTED:" : "UNMAPPED:")
                            + DigestUtils.sha256Hex(userId).substring(0, 16),
                    content, "TEST_REPORT", reportId, "EVENT", null, null);
            finishDelivery(failedId, false, false,
                    mappedButOmitted ? "MENTION_LIMIT_EXCEEDED" : "MISSING_WECOM_USERID",
                    mappedButOmitted
                            ? "Recipient mention was omitted because the WeCom message size limit was reached"
                            : "Selected or resolved recipient cannot be mentioned because no active WeCom userid mapping exists");
            jdbc.update("UPDATE wecom_notification_outbox SET recipient_user_id=?,configured_recipient_spec=?,resolved_user_ids=?,mentioned_user_ids=?,update_time=? WHERE id=?",
                    userId, json(recipient), json(users), json(mentions.includedUserIds()), System.currentTimeMillis(), failedId);
            result.add(failedId);
        }
        return result;
    }

    private Map<String, String> mappedWecomUsers(Collection<String> userIds) {
        if (userIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(userIds.size(), "?"));
        Map<String, String> result = new LinkedHashMap<>();
        jdbc.queryForList("SELECT id,wecom_userid FROM user WHERE enable=1 AND deleted=0 AND wecom_userid IS NOT NULL AND wecom_userid<>'' AND id IN (" + placeholders + ")",
                userIds.toArray()).forEach(row -> result.put(str(row, "id"), str(row, "wecom_userid")));
        return result;
    }

    private Map<String, String> userNamesById(Collection<String> userIds) {
        if (userIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(userIds.size(), "?"));
        Map<String, String> result = new LinkedHashMap<>();
        jdbc.queryForList("SELECT id,name FROM user WHERE id IN (" + placeholders + ")", userIds.toArray())
                .forEach(row -> result.put(str(row, "id"), str(row, "name")));
        return result;
    }

    private MentionResult appendMentions(String content, Map<String, String> mappedUsers) {
        if (mappedUsers.isEmpty()) return new MentionResult(content, Set.of());
        int available = 20_480 - content.getBytes(StandardCharsets.UTF_8).length;
        StringBuilder suffix = new StringBuilder("\n\n");
        Set<String> included = new LinkedHashSet<>();
        for (Map.Entry<String, String> user : mappedUsers.entrySet()) {
            String token = "<@" + user.getValue() + "> ";
            if ((suffix.toString() + token).getBytes(StandardCharsets.UTF_8).length > available) break;
            suffix.append(token);
            included.add(user.getKey());
        }
        return new MentionResult(suffix.length() == 2 ? content : content + suffix, included);
    }

    private record MentionResult(String content, Set<String> includedUserIds) { }

    private void validateTerminalStatuses(Map<String, Object> stopConfig, Map<String, Object> triggerConfig) {
        Object configured = stopConfig == null ? null : stopConfig.get("statuses");
        if (!(configured instanceof Collection<?>)) configured = triggerConfig == null ? null : triggerConfig.get("terminalStatuses");
        if (!(configured instanceof Collection<?> values) || values.isEmpty()) return;
        List<String> ids = values.stream().map(String::valueOf).distinct().toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM status_item si JOIN workflow_definition wd ON wd.id=si.flow_id WHERE si.id IN (" + placeholders + ") AND si.enabled=1 AND si.terminal_status=1 AND wd.scene='BUG' AND wd.lifecycle='PUBLISHED' AND wd.enabled=1", Long.class, ids.toArray());
        if (count == null || count != ids.size()) throw new MSException("One or more terminal statuses are invalid");
    }

    private void requireBotEnabled() {
        if (!isEnabled()) throw new MSException("WeCom Bot is disabled");
    }

    private void refreshDeadlineResources(String ruleId) {
        Map<String, Object> rule = rule(ruleId);
        List<Map<String, Object>> bugs = "PROJECT".equals(str(rule, "scope_type"))
                ? jdbc.queryForList("SELECT id,update_time FROM bug WHERE project_id=? AND expected_resolve_time IS NOT NULL AND deleted=0", str(rule, "scope_id"))
                : jdbc.queryForList("SELECT id,update_time FROM bug WHERE expected_resolve_time IS NOT NULL AND deleted=0");
        bugs.forEach(bug -> events.publishEvent(new BugExpectedResolutionChangedEvent(str(bug, "id"),
                bug.get("update_time") instanceof Number n ? n.longValue() : System.currentTimeMillis())));
    }

    public void recordCronFire(String ruleId, long fireTime) {
        cronSchedules.recordFire(ruleId, fireTime);
    }

    public List<WecomBotModels.TemplateVariable> templateVariables(String notificationType) {
        return providers.variables(notificationType);
    }

    public Map<String, Object> recipientPreview(WecomBotModels.RuleRequest request) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("scope_type", request.scopeType());
        rule.put("scope_id", request.scopeId());
        rule.put("notification_type", request.notificationType());
        Map<String, Object> recipient = normalizeRecipients(request.recipientSpec());
        Set<String> users = new LinkedHashSet<>(resolveRecipientUsers(rule, recipient));
        if ("TEST_REPORT_GENERATED".equals(request.notificationType())
                && strings(recipient.get("userIds")).isEmpty() && StringUtils.isNotBlank(request.scopeId())) {
            users.addAll(jdbc.queryForList("SELECT DISTINCT u.id FROM user_role_relation urr JOIN user u ON u.id=urr.user_id WHERE urr.source_id=? AND u.enable=1 AND u.deleted=0",
                    String.class, request.scopeId()));
        }
        if (users.isEmpty()) {
            return Map.of("users", List.of(), "warnings", List.of("未解析到有效接收人"));
        }
        String placeholders = String.join(",", Collections.nCopies(users.size(), "?"));
        List<Object> args = new ArrayList<>();
        String projectMemberSql = StringUtils.isBlank(request.scopeId()) ? "false"
                : "EXISTS(SELECT 1 FROM user_role_relation pm WHERE pm.user_id=u.id AND pm.source_id=?)";
        if (StringUtils.isNotBlank(request.scopeId())) args.add(request.scopeId());
        args.addAll(users);
        List<Map<String, Object>> resolved = jdbc.queryForList("SELECT u.id,u.name,u.position,(u.wecom_userid IS NOT NULL AND u.wecom_userid<>'') wecomMapped," + projectMemberSql + " projectMember FROM user u WHERE u.enable=1 AND u.deleted=0 AND u.id IN (" + placeholders + ") ORDER BY u.name", args.toArray());
        boolean groupMention = "TEST_REPORT_GENERATED".equals(request.notificationType());
        resolved.forEach(user -> {
            boolean mapped = bool(user, "wecomMapped");
            user.put("willMention", groupMention && mapped);
            user.put("unavailableReason", mapped ? null : "MISSING_WECOM_USERID");
        });
        List<String> warnings = resolved.stream().filter(item -> !bool(item, "wecomMapped"))
                .map(item -> item.get("name") + " 未绑定企微账号，群消息仍会发送但无法 @ 此人").toList();
        return Map.of("users", resolved, "warnings", warnings);
    }

    public List<Map<String, Object>> schedules(String ruleId) {
        return jdbc.queryForList("SELECT id,rule_id,cycle_type,weekdays,execution_time,timezone,enabled,next_fire_time,last_fire_time,create_time,update_time FROM wecom_notification_schedule WHERE rule_id=? ORDER BY execution_time,create_time", ruleId);
    }

    public Map<String, Object> schedule(String id) {
        return new LinkedHashMap<>(requiredSchedule(id));
    }

    @Transactional
    public String createSchedule(String ruleId, WecomBotModels.ScheduleRequest request, String userId) {
        Map<String, Object> rule = rule(ruleId);
        validateSchedules(str(rule, "notification_type"), List.of(request), ruleId, null);
        long now = System.currentTimeMillis();
        String id = IDGenerator.nextStr();
        jdbc.update("INSERT INTO wecom_notification_schedule(id,rule_id,cycle_type,weekdays,execution_time,timezone,enabled,create_time,update_time,create_user,update_user) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                id, ruleId, request.cycleType(), weekdays(request), request.executionTime(), request.timezone(),
                !Boolean.FALSE.equals(request.enabled()), now, now, userId, userId);
        requestScheduleReconcile(ruleId, Set.of(), false);
        return id;
    }

    @Transactional
    public void updateSchedule(String id, WecomBotModels.ScheduleRequest request, String userId) {
        Map<String, Object> previous = requiredSchedule(id);
        Map<String, Object> rule = rule(str(previous, "rule_id"));
        validateSchedules(str(rule, "notification_type"), List.of(request), str(previous, "rule_id"), id);
        requireUpdated(jdbc.update("UPDATE wecom_notification_schedule SET cycle_type=?,weekdays=?,execution_time=?,timezone=?,enabled=?,next_fire_time=NULL,update_time=?,update_user=? WHERE id=?",
                request.cycleType(), weekdays(request), request.executionTime(), request.timezone(),
                !Boolean.FALSE.equals(request.enabled()), System.currentTimeMillis(), userId, id));
        requestScheduleReconcile(str(previous, "rule_id"), Set.of(), false);
    }

    @Transactional
    public void deleteSchedule(String id) {
        Map<String, Object> schedule = requiredSchedule(id);
        requireUpdated(jdbc.update("DELETE FROM wecom_notification_schedule WHERE id=?", id));
        requestScheduleReconcile(str(schedule, "rule_id"), Set.of(id), false);
    }

    @Transactional
    public void enableSchedule(String id, boolean enabled, String userId) {
        Map<String, Object> schedule = requiredSchedule(id);
        if (enabled) validateSchedules(str(rule(str(schedule, "rule_id")), "notification_type"),
                List.of(toScheduleRequest(schedule, true)), str(schedule, "rule_id"), id);
        requireUpdated(jdbc.update("UPDATE wecom_notification_schedule SET enabled=?,next_fire_time=NULL,update_time=?,update_user=? WHERE id=?",
                enabled, System.currentTimeMillis(), userId, id));
        requestScheduleReconcile(str(schedule, "rule_id"), Set.of(), false);
    }

    private void syncSchedules(String ruleId, String notificationType, List<WecomBotModels.ScheduleRequest> requests,
                               String userId, boolean ruleEnabled) {
        if (requests == null) return;
        validateSchedules(notificationType, requests, null, null);
        Map<String, Map<String, Object>> existing = new HashMap<>();
        schedules(ruleId).forEach(item -> existing.put(str(item, "id"), item));
        jdbc.update("UPDATE wecom_notification_schedule SET enabled=0,next_fire_time=NULL,update_time=? WHERE rule_id=?",
                System.currentTimeMillis(), ruleId);
        Set<String> retained = new HashSet<>();
        for (WecomBotModels.ScheduleRequest request : requests) {
            if (StringUtils.isNotBlank(request.id()) && existing.containsKey(request.id())) {
                retained.add(request.id());
                updateSchedule(request.id(), request, userId);
            } else {
                retained.add(createSchedule(ruleId, request, userId));
            }
        }
        Set<String> removed = existing.keySet().stream().filter(id -> !retained.contains(id)).collect(java.util.stream.Collectors.toSet());
        removed.forEach(id -> requireUpdated(jdbc.update("DELETE FROM wecom_notification_schedule WHERE id=?", id)));
        requestScheduleReconcile(ruleId, removed, false);
    }

    private void validateSchedules(String notificationType, List<WecomBotModels.ScheduleRequest> schedules,
                                   String ruleId, String excludedId) {
        if (schedules == null || schedules.isEmpty()) return;
        if (!"BUG_EXPECTED_RESOLUTION_DUE".equals(notificationType)) {
            throw new MSException("Daily and weekly schedules are only supported by bug expected-resolution notifications");
        }
        Set<String> enabledDefinitions = new HashSet<>();
        for (WecomBotModels.ScheduleRequest schedule : schedules) {
            if (!Set.of("DAILY", "WEEKLY").contains(schedule.cycleType())) throw new MSException("Schedule cycle must be DAILY or WEEKLY");
            try { ZoneId.of(schedule.timezone()); } catch (Exception e) { throw new MSException("Invalid schedule timezone"); }
            Map<String, Object> candidate = new HashMap<>();
            candidate.put("cycle_type", schedule.cycleType());
            candidate.put("weekdays", weekdays(schedule));
            candidate.put("execution_time", schedule.executionTime());
            cronSchedules.cronFor(candidate);
            if (!Boolean.FALSE.equals(schedule.enabled())) {
                String definition = schedule.cycleType() + "|" + weekdays(schedule) + "|" + schedule.executionTime() + "|" + schedule.timezone();
                if (!enabledDefinitions.add(definition)) throw new MSException("Duplicate enabled notification schedule");
                List<Object> args = new ArrayList<>(List.of(schedule.cycleType(), weekdays(schedule), schedule.executionTime(), schedule.timezone()));
                String sql = "SELECT COUNT(*) FROM wecom_notification_schedule WHERE enabled=1 AND cycle_type=? AND COALESCE(weekdays,'')=COALESCE(?,'') AND execution_time=? AND timezone=?";
                if (StringUtils.isNotBlank(ruleId)) { sql += " AND rule_id=?"; args.add(ruleId); }
                if (StringUtils.isNotBlank(excludedId)) { sql += " AND id<>?"; args.add(excludedId); }
                if (StringUtils.isNotBlank(ruleId)) {
                    Long duplicate = jdbc.queryForObject(sql, Long.class, args.toArray());
                    if (duplicate != null && duplicate > 0) throw new MSException("Duplicate enabled notification schedule");
                }
            }
        }
    }

    private Map<String, Object> requiredSchedule(String id) {
        Map<String, Object> schedule = one("SELECT * FROM wecom_notification_schedule WHERE id=?", id);
        if (schedule == null) throw new MSException("Notification schedule does not exist");
        return schedule;
    }

    private List<Map<String, Object>> enabledSchedules(String ruleId) {
        return jdbc.queryForList("SELECT * FROM wecom_notification_schedule WHERE rule_id=? AND enabled=1", ruleId);
    }

    private void requestScheduleReconcile(String ruleId, Set<String> removedScheduleIds, boolean deleted) {
        events.publishEvent(new WecomNotificationCronScheduleService.ReconcileRequest(ruleId, removedScheduleIds, deleted));
    }

    private String weekdays(WecomBotModels.ScheduleRequest request) {
        if (!"WEEKLY".equals(request.cycleType())) return null;
        if (request.weekdays() == null || request.weekdays().isEmpty()) throw new MSException("Weekly schedule requires weekdays");
        return request.weekdays().stream().distinct().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private WecomBotModels.ScheduleRequest toScheduleRequest(Map<String, Object> schedule, boolean enabled) {
        List<Integer> days = StringUtils.isBlank(str(schedule, "weekdays")) ? List.of()
                : Arrays.stream(str(schedule, "weekdays").split(",")).map(Integer::valueOf).toList();
        return new WecomBotModels.ScheduleRequest(str(schedule, "id"), str(schedule, "cycle_type"), days,
                str(schedule, "execution_time"), str(schedule, "timezone"), enabled);
    }

    private Map<String, Object> normalizeRecipients(Map<String, Object> source) {
        Map<String, Object> recipients = source == null ? new HashMap<>() : new HashMap<>(source);
        recipients.remove("userGroupIds");
        recipients.remove("projectRoleIds");
        recipients.put("chatIds", strings(recipients.get("chatIds")));
        recipients.put("userIds", strings(recipients.get("userIds")));
        recipients.put("positionIds", strings(recipients.get("positionIds")));
        recipients.put("roleIds", strings(recipients.get("roleIds")));
        return recipients;
    }

    private Map<String, Object> recipientsForRule(WecomBotModels.RuleRequest request) {
        Map<String, Object> recipients = normalizeRecipients(request.recipientSpec());
        if ("BUG_EXPECTED_RESOLUTION_DUE".equals(request.notificationType())
                && strings(recipients.get("chatIds")).isEmpty()
                && strings(recipients.get("userIds")).isEmpty()
                && strings(recipients.get("positionIds")).isEmpty()
                && strings(recipients.get("roleIds")).isEmpty()
                && strings(recipients.get("businessRoles")).isEmpty()) {
            recipients.put("businessRoles", List.of("BUG_CREATOR", "BUG_HANDLER"));
        }
        return recipients;
    }

    private boolean usesRulePeriod(String notificationType) {
        return "CUSTOM_CRON".equals(notificationType);
    }

    public boolean hasEnabledSchedule(String ruleId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM wecom_notification_schedule WHERE rule_id=? AND enabled=1", Long.class, ruleId);
        return count != null && count > 0;
    }

    private List<String> executeBugRule(Map<String, Object> rule, String triggerPrefix, String triggerMode,
                                        String triggerUserId, String scheduleId, long now) {
        StringBuilder sql = new StringBuilder("SELECT b.id,b.num,b.title,b.status,b.handle_user,b.create_user AS bug_create_user,b.expected_resolve_time,b.project_id,COALESCE(si.name,b.status) bug_status_name,p.name project_name FROM bug b JOIN project p ON p.id=b.project_id AND p.enable=1 AND p.deleted=0 LEFT JOIN status_item si ON si.id=b.status AND si.enabled=1 WHERE b.deleted=0 AND b.expected_resolve_time IS NOT NULL AND b.expected_resolve_time>?");
        List<Object> args = new ArrayList<>();
        args.add(now);
        if ("PROJECT".equals(str(rule, "scope_type"))) {
            sql.append(" AND b.project_id=?");
            args.add(str(rule, "scope_id"));
        }
        sql.append(" ORDER BY b.expected_resolve_time,b.id");
        Map<String, Object> trigger = parseMap(str(rule, "trigger_config"));
        long leadAmount = number(trigger.get("leadTime"), number(trigger.get("beforeMinutes"), 60L));
        long leadMillis = durationMillis(leadAmount, Objects.toString(trigger.get("leadUnit"), "MINUTE"));
        Set<String> terminalStatuses = new HashSet<>(strings(parseMap(str(rule, "stop_config")).get("statuses")));
        if (terminalStatuses.isEmpty()) terminalStatuses.addAll(strings(trigger.get("terminalStatuses")));
        List<String> result = new ArrayList<>();
        for (Map<String, Object> bug : jdbc.queryForList(sql.toString(), args.toArray())) {
            long deadline = ((Number) bug.get("expected_resolve_time")).longValue();
            if (leadMillis > 0 && deadline - now > leadMillis) continue;
            if (terminalStatuses.contains(str(bug, "status"))) continue;
            Map<String, Object> variables = new HashMap<>();
            variables.put("bugNum", bug.get("num"));
            variables.put("bugTitle", bug.get("title"));
            variables.put("bugStatus", bug.get("bug_status_name"));
            variables.put("bugHandlerNames", userNames(userIds(bug.get("handle_user"))));
            variables.put("bugCreatorName", userNames(userIds(bug.get("bug_create_user"))));
            variables.put("expectedResolveTime", formatTimestamp(deadline, str(rule, "timezone")));
            variables.put("remainingTime", formatRemaining(deadline - now));
            variables.put("projectName", bug.get("project_name"));
            variables.put("resourceUrl", baseUrl() + "/bug-management/detail/edit?id=" + bug.get("id"));
            variables.put("ruleName", rule.get("name"));
            variables.put("now", formatTimestamp(now, str(rule, "timezone")));
            Map<String, Object> deliveryRule = withBugBusinessRecipients(rule, bug);
            result.addAll(enqueueForRule(deliveryRule, triggerPrefix + ":" + bug.get("id"), "BUG",
                    str(bug, "id"), render(str(rule, "template"), variables), triggerMode, triggerUserId, scheduleId));
        }
        return result;
    }

    private Map<String, Object> withBugBusinessRecipients(Map<String, Object> rule, Map<String, Object> bug) {
        Map<String, Object> copy = new HashMap<>(rule);
        Map<String, Object> recipient = normalizeRecipients(parseMap(str(rule, "recipient_spec")));
        Set<String> users = new LinkedHashSet<>(strings(recipient.get("userIds")));
        List<String> businessRoles = strings(recipient.get("businessRoles"));
        if (businessRoles.isEmpty()) businessRoles = List.of("BUG_CREATOR", "BUG_HANDLER");
        if (businessRoles.contains("BUG_CREATOR")) users.addAll(userIds(bug.get("bug_create_user")));
        if (businessRoles.contains("BUG_HANDLER")) users.addAll(userIds(bug.get("handle_user")));
        recipient.put("userIds", users);
        copy.put("recipient_spec", json(recipient));
        return copy;
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

    private String userNames(Set<String> ids) {
        if (ids.isEmpty()) return "-";
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<String> names = jdbc.queryForList("SELECT name FROM user WHERE id IN (" + placeholders + ") AND enable=1 AND deleted=0 ORDER BY name",
                String.class, ids.toArray());
        return names.isEmpty() ? "-" : String.join(", ", names);
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

    String formatTimestamp(long timestamp, String timezone) {
        ZoneId zone;
        try { zone = ZoneId.of(StringUtils.defaultIfBlank(timezone, "Asia/Shanghai")); }
        catch (Exception ignored) { zone = ZoneId.of("Asia/Shanghai"); }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone).format(Instant.ofEpochMilli(timestamp));
    }

    String formatRemaining(long millis) {
        long minutes = Math.max(0, millis) / 60_000L;
        if (minutes >= 1_440) return (minutes / 1_440) + "天" + (minutes % 1_440 / 60) + "小时";
        if (minutes >= 60) return (minutes / 60) + "小时" + (minutes % 60) + "分钟";
        return minutes + "分钟";
    }

    private String baseUrl() {
        List<String> values = jdbc.queryForList("SELECT param_value FROM system_parameter WHERE param_key='base.url' LIMIT 1", String.class);
        return values.isEmpty() ? "" : StringUtils.removeEnd(values.getFirst(), "/");
    }

    private Map<String, Object> requiredConfig() {
        Map<String, Object> result = configRow();
        if (result == null) throw new MSException("WeCom Bot is not configured");
        return result;
    }

    private Map<String, Object> configRow() {
        return one("SELECT * FROM wecom_bot_config ORDER BY create_time LIMIT 1");
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = args.length == 0 ? jdbc.queryForList(sql) : jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void requireUpdated(int count) {
        if (count == 0) throw new MSException("Resource does not exist or cannot be updated in its current state");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String value) {
        return StringUtils.isBlank(value) ? new HashMap<>() : JSON.parseObject(value, Map.class);
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        return values.stream().filter(Objects::nonNull).map(String::valueOf).filter(StringUtils::isNotBlank).distinct().toList();
    }

    private String json(Object value) {
        return JSON.toJSONString(value == null ? Map.of() : value);
    }

    private String preview(String content) {
        String clean = content.replaceAll("[\\r\\n]+", " ");
        return clean.length() > 500 ? clean.substring(0, 500) : clean;
    }

    private String safeError(String error) {
        if (error == null) return null;
        String clean = error.replaceAll("(?i)(secret|token|authorization)\\s*[:=]\\s*[^\\s,;]+", "$1=***");
        return clean.length() > 500 ? clean.substring(0, 500) : clean;
    }

    private String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() == 1;
    }

    private Long lng(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number n ? n.longValue() : null;
    }
}
