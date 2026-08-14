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
import io.metersphere.system.event.BugExpectedResolutionChangedEvent;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.ZoneId;
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
        return jdbc.queryForList("SELECT * FROM wecom_notification_rule ORDER BY update_time DESC");
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
        jdbc.update("INSERT INTO wecom_notification_rule(id,name,scope_type,scope_id,bot_config_id,notification_type,trigger_type,trigger_config,cron,timezone,enabled,message_type,template,recipient_spec,delivery_mode,stop_config,misfire_policy,start_at,end_at,version,create_time,update_time,create_user,update_user) VALUES(?,?,?,?,?,?,?,?,?,?,0,'MARKDOWN',?,?,?,?, 'DO_NOTHING',?,?,1,?,?,?,?)",
                id, request.name(), request.scopeType(), request.scopeId(), str(config, "id"), request.notificationType(),
                request.triggerType(), json(request.triggerConfig()), request.cron(), request.timezone(), request.template(),
                json(request.recipientSpec()), request.deliveryMode(), json(request.stopConfig()), request.startAt(), request.endAt(),
                now, now, userId, userId);
        return id;
    }

    @Transactional
    public void updateRule(String id, WecomBotModels.RuleRequest request, String userId) {
        validateRule(request, userId);
        Map<String, Object> previous = rule(id);
        requireUpdated(jdbc.update("UPDATE wecom_notification_rule SET name=?,scope_type=?,scope_id=?,notification_type=?,trigger_type=?,trigger_config=?,cron=?,timezone=?,template=?,recipient_spec=?,delivery_mode=?,stop_config=?,start_at=?,end_at=?,version=version+1,update_time=?,update_user=? WHERE id=?",
                request.name(), request.scopeType(), request.scopeId(), request.notificationType(), request.triggerType(),
                json(request.triggerConfig()), request.cron(), request.timezone(), request.template(), json(request.recipientSpec()),
                request.deliveryMode(), json(request.stopConfig()), request.startAt(), request.endAt(), System.currentTimeMillis(), userId, id));
        jdbc.update("UPDATE wecom_notification_timer SET status='CANCELLED',update_time=? WHERE rule_id=? AND status IN ('WAITING','PROCESSING')", System.currentTimeMillis(), id);
        if ("CRON".equals(str(previous, "trigger_type")) && !"CRON".equals(request.triggerType())) cronSchedules.remove(id);
        if (bool(previous, "enabled") && "CRON".equals(request.triggerType())) cronSchedules.schedule(id, request.cron(), request.timezone());
        if (bool(previous, "enabled") && "DEADLINE".equals(request.triggerType())) refreshDeadlineResources(id);
    }

    @Transactional
    public void deleteRule(String id) {
        cronSchedules.remove(id);
        jdbc.update("UPDATE wecom_notification_timer SET status='CANCELLED',update_time=? WHERE rule_id=?", System.currentTimeMillis(), id);
        jdbc.update("UPDATE wecom_notification_outbox SET status='CANCELLED',next_retry_at=NULL,lease_until=NULL,update_time=? WHERE rule_id=? AND status IN ('PENDING','RETRY')", System.currentTimeMillis(), id);
        requireUpdated(jdbc.update("DELETE FROM wecom_notification_rule WHERE id=?", id));
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
        if (enabled) requireActiveRecipient(str(row, "recipient_spec"));
        if (enabled) validateDeliveryRecipients(str(row, "delivery_mode"), recipients);
        if (enabled && "CRON".equals(str(row, "trigger_type"))) {
            cronSchedules.schedule(id, str(row, "cron"), str(row, "timezone"));
        }
        requireUpdated(jdbc.update("UPDATE wecom_notification_rule SET enabled=?,version=version+1,update_time=?,update_user=? WHERE id=?",
                enabled, System.currentTimeMillis(), userId, id));
        if (!enabled) {
            jdbc.update("UPDATE wecom_notification_timer SET status='CANCELLED',update_time=? WHERE rule_id=? AND status IN ('WAITING','PROCESSING')", System.currentTimeMillis(), id);
            jdbc.update("UPDATE wecom_notification_outbox SET status='CANCELLED',next_retry_at=NULL,lease_until=NULL,update_time=? WHERE rule_id=? AND status IN ('PENDING','RETRY')", System.currentTimeMillis(), id);
        }
        if ("CRON".equals(str(row, "trigger_type"))) {
            if (!enabled) cronSchedules.remove(id);
        }
        if (enabled && "DEADLINE".equals(str(row, "trigger_type"))) refreshDeadlineResources(id);
    }

    public String preview(String id, Map<String, Object> variables) {
        return render(str(rule(id), "template"), variables == null ? Map.of() : variables);
    }

    @Transactional
    public List<String> runOnce(String id) {
        requireBotEnabled();
        Map<String, Object> row = rule(id);
        if (!"CUSTOM_CRON".equals(str(row, "notification_type"))) {
            throw new MSException("Only custom Cron rules support immediate execution");
        }
        if (!bool(row, "enabled")) throw new MSException("Enable the rule before immediate execution");
        String content = render(str(row, "template"), Map.of("ruleName", str(row, "name"), "now", System.currentTimeMillis(),
                "customTitle", str(row, "name"), "customContent", ""));
        List<String> ids = enqueueForRule(row, "MANUAL:" + id + ":" + IDGenerator.nextStr(), null, null, content);
        if (ids.isEmpty()) throw new MSException("No valid recipient was resolved");
        return ids;
    }

    public WecomBotModels.PageResult<Map<String, Object>> logs(int page, int pageSize, String status, String eventType,
                                                               String targetType, String ruleId, Long startAt, Long endAt) {
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        int offset = Math.max(page - 1, 0) * safeSize;
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> values = new ArrayList<>();
        addFilter(where, values, "status", status);
        addFilter(where, values, "event_type", eventType);
        addFilter(where, values, "target_type", targetType);
        addFilter(where, values, "rule_id", ruleId);
        if (startAt != null) { where.append(" AND create_time>=?"); values.add(startAt); }
        if (endAt != null) { where.append(" AND create_time<=?"); values.add(endAt); }
        List<Object> pageValues = new ArrayList<>(values);
        pageValues.add(safeSize);
        pageValues.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,rule_id,resource_type,resource_id,event_type,target_type,payload_preview,status,attempts,max_attempts,next_retry_at,error_code,error_message,retryable,create_time,update_time,sent_at FROM wecom_notification_outbox" + where + " ORDER BY create_time DESC LIMIT ? OFFSET ?", pageValues.toArray());
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM wecom_notification_outbox" + where, Long.class, values.toArray());
        return new WecomBotModels.PageResult<>(rows, total == null ? 0 : total);
    }

    private void addFilter(StringBuilder where, List<Object> values, String column, String value) {
        if (StringUtils.isNotBlank(value)) { where.append(" AND ").append(column).append("=?"); values.add(value); }
    }

    public Map<String, Object> log(String id) {
        Map<String, Object> row = one("SELECT id,rule_id,resource_type,resource_id,event_type,target_type,payload_preview,payload_hash,status,attempts,max_attempts,next_retry_at,request_id,error_code,error_message,create_time,update_time,sent_at FROM wecom_notification_outbox WHERE id=?", id);
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
        if (StringUtils.isBlank(content) || content.getBytes(StandardCharsets.UTF_8).length > 20_480) {
            throw new MSException("Message content must be between 1 and 20480 bytes");
        }
        String id = IDGenerator.nextStr();
        long now = System.currentTimeMillis();
        String payload = JSON.toJSONString(Map.of("content", content));
        try {
            jdbc.update("INSERT INTO wecom_notification_outbox(id,rule_id,resource_type,resource_id,event_type,event_id,trigger_key,target_type,target_id,message_type,payload,payload_preview,payload_hash,status,attempts,max_attempts,next_retry_at,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,'MARKDOWN',?,?,?,'PENDING',0,4,?,?,?)",
                    id, ruleId, resourceType, resourceId, eventType, eventId, triggerKey, targetType, targetId, payload,
                    preview(content), DigestUtils.sha256Hex(payload), now, now, now);
            return id;
        } catch (DuplicateKeyException e) {
            return jdbc.queryForObject("SELECT id FROM wecom_notification_outbox WHERE trigger_key=? AND target_type=? AND target_id=?", String.class, triggerKey, targetType, targetId);
        }
    }

    public List<String> enqueueForRule(Map<String, Object> rule, String triggerKey, String resourceType,
                                       String resourceId, String content) {
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
                        triggerKey, "CHAT", chatId, content, resourceType, resourceId);
                result.add(outboxId);
                if (!activeChat(chatId)) finishDelivery(outboxId, false, false, "INACTIVE_CHAT", "Recipient group is not discovered or enabled");
            }
        }
        if (sendUsers) {
            for (String userId : resolveRecipientUsers(rule, recipient)) {
                String wecomId = mappedWecomUser(userId);
                if (wecomId != null) {
                    result.add(enqueue(str(rule, "id"), str(rule, "notification_type"), triggerKey,
                            triggerKey, "USER", wecomId, content, resourceType, resourceId));
                } else {
                    String failedId = enqueue(str(rule, "id"), str(rule, "notification_type"), triggerKey,
                            triggerKey, "USER", "UNMAPPED:" + DigestUtils.sha256Hex(userId).substring(0, 16), content, resourceType, resourceId);
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
            if (request.recipientSpec() != null) {
                for (String recipientId : strings(request.recipientSpec().get("userIds"))) {
                    Long member = jdbc.queryForObject("SELECT COUNT(*) FROM user_role_relation urr JOIN user u ON u.id=urr.user_id WHERE urr.source_id=? AND urr.user_id=? AND u.enable=1 AND u.deleted=0", Long.class, request.scopeId(), recipientId);
                    if (member == null || member == 0) throw new MSException("Recipient is not an active member of the selected project");
                }
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
            throw new MSException("Invalid Cron expression");
        }
        try {
            ZoneId.of(request.timezone());
        } catch (Exception e) {
            throw new MSException("Invalid timezone");
        }
        if (request.startAt() != null && request.endAt() != null && request.startAt() >= request.endAt()) {
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
        validateTemplate(request.template());
        Map<String, Object> recipients = request.recipientSpec() == null ? Map.of() : request.recipientSpec();
        if ("BUG_EXPECTED_RESOLUTION_DUE".equals(request.notificationType())
                && strings(recipients.get("chatIds")).isEmpty() && strings(recipients.get("userIds")).isEmpty()
                && strings(recipients.get("businessRoles")).isEmpty()) {
            recipients = new HashMap<>(recipients);
            recipients.put("businessRoles", List.of("BUG_CREATOR", "BUG_HANDLER"));
        }
        requireActiveRecipient(json(recipients));
        validateDeliveryRecipients(request.deliveryMode(), recipients);
        validateRecipientSelectors(request, recipients);
        if ("BUG_EXPECTED_RESOLUTION_DUE".equals(request.notificationType())
                && strings(recipients.get("chatIds")).isEmpty() && strings(recipients.get("userIds")).isEmpty()
                && !List.of("USER", "BOTH").contains(request.deliveryMode())) {
            throw new MSException("Default bug creator and handler recipients require personal delivery");
        }
        if ("TEST_REPORT_GENERATED".equals(request.notificationType()) && strings(recipients.get("chatIds")).isEmpty()) {
            throw new MSException("Test report notification requires at least one enabled group");
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
                || !strings(recipient.get("projectRoleIds")).isEmpty()
                || !strings(recipient.get("userGroupIds")).isEmpty()
                || !strings(recipient.get("businessRoles")).isEmpty();
        if (chatIds.isEmpty() && userIds.isEmpty() && !dynamic) throw new MSException("At least one recipient is required");
        for (String chatId : chatIds) if (!activeChat(chatId)) throw new MSException("Recipient group is not discovered or enabled");
    }

    private void validateDeliveryRecipients(String deliveryMode, Map<String, Object> recipient) {
        boolean chats = !strings(recipient.get("chatIds")).isEmpty();
        boolean users = !strings(recipient.get("userIds")).isEmpty()
                || Boolean.TRUE.equals(recipient.get("projectAllMembers"))
                || !strings(recipient.get("projectRoleIds")).isEmpty()
                || !strings(recipient.get("userGroupIds")).isEmpty()
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
            throw new MSException("Invalid dynamic business recipient role");
        }
        Set<String> roleIds = new LinkedHashSet<>(strings(recipient.get("projectRoleIds")));
        roleIds.addAll(strings(recipient.get("userGroupIds")));
        if (roleIds.isEmpty()) return;
        String placeholders = String.join(",", Collections.nCopies(roleIds.size(), "?"));
        List<Object> args = new ArrayList<>(roleIds);
        String sql = "SELECT COUNT(*) FROM user_role WHERE enabled=1 AND id IN (" + placeholders + ")";
        if ("PROJECT".equals(request.scopeType())) {
            sql += " AND (type='SYSTEM' OR (type='PROJECT' AND scope_id=?))";
            args.add(request.scopeId());
        } else {
            sql += " AND type<>'PROJECT'";
        }
        Long count = jdbc.queryForObject(sql, Long.class, args.toArray());
        if (count == null || count != roleIds.size()) throw new MSException("One or more recipient roles are invalid for the selected scope");
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
            return jdbc.queryForList("SELECT id,name,(wecom_userid IS NOT NULL AND wecom_userid<>'') mapped FROM user WHERE enable=1 AND deleted=0 ORDER BY name LIMIT 1000");
        }
        return jdbc.queryForList("SELECT DISTINCT u.id,u.name,(u.wecom_userid IS NOT NULL AND u.wecom_userid<>'') mapped FROM user_role_relation urr JOIN user u ON u.id=urr.user_id WHERE urr.source_id=? AND u.enable=1 AND u.deleted=0 ORDER BY u.name LIMIT 1000", projectId);
    }

    public List<Map<String, Object>> roleOptions(String projectId) {
        if (StringUtils.isBlank(projectId)) {
            return jdbc.queryForList("SELECT id,name,type,scope_id FROM user_role WHERE enabled=1 ORDER BY type,name LIMIT 1000");
        }
        return jdbc.queryForList("SELECT id,name,type,scope_id FROM user_role WHERE enabled=1 AND (type='SYSTEM' OR (type='PROJECT' AND scope_id=?)) ORDER BY type,name LIMIT 1000", projectId);
    }

    public List<Map<String, Object>> bugTerminalStatuses() {
        return jdbc.queryForList("SELECT si.id,si.name,si.status_code FROM status_item si JOIN workflow_definition wd ON wd.id=si.flow_id WHERE wd.scene='BUG' AND wd.lifecycle='PUBLISHED' AND wd.default_flow=1 AND wd.enabled=1 AND si.enabled=1 AND si.terminal_status=1 ORDER BY si.pos");
    }

    private Set<String> resolveRecipientUsers(Map<String, Object> rule, Map<String, Object> recipient) {
        Set<String> users = new LinkedHashSet<>(strings(recipient.get("userIds")));
        String projectId = str(rule, "scope_id");
        if (Boolean.TRUE.equals(recipient.get("projectAllMembers")) && StringUtils.isNotBlank(projectId)) {
            users.addAll(jdbc.queryForList("SELECT DISTINCT user_id FROM user_role_relation WHERE source_id=?", String.class, projectId));
        }
        Set<String> roleIds = new LinkedHashSet<>(strings(recipient.get("projectRoleIds")));
        roleIds.addAll(strings(recipient.get("userGroupIds")));
        if (!roleIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(roleIds.size(), "?"));
            List<Object> args = new ArrayList<>(roleIds);
            String sql = "SELECT DISTINCT urr.user_id FROM user_role_relation urr JOIN user_role ur ON ur.id=urr.role_id AND ur.enabled=1 WHERE urr.role_id IN (" + placeholders + ")";
            if (StringUtils.isNotBlank(projectId)) { sql += " AND (urr.source_id=? OR ur.type='SYSTEM')"; args.add(projectId); }
            users.addAll(jdbc.queryForList(sql, String.class, args.toArray()));
        }
        return users;
    }

    Set<String> resolveRecipientUsers(Map<String, Object> rule) {
        return resolveRecipientUsers(rule, parseMap(str(rule, "recipient_spec")));
    }

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

    private Map<String, Object> requiredConfig() {
        Map<String, Object> result = configRow();
        if (result == null) throw new MSException("WeCom Bot is not configured");
        return result;
    }

    private Map<String, Object> configRow() {
        return one("SELECT * FROM wecom_bot_config ORDER BY create_time LIMIT 1");
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
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
