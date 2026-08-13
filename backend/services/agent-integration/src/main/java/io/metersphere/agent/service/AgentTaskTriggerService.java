package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionCreateRequest;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentTaskTriggerDTO;
import io.metersphere.agent.dto.AgentTaskTriggerEventRequest;
import io.metersphere.agent.dto.AgentTaskTriggerHistoryDTO;
import io.metersphere.agent.dto.AgentTaskTriggerRequest;
import io.metersphere.agent.mapper.AgentTaskTriggerMapper;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.EncryptUtils;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.quartz.CronExpression;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

@Service
public class AgentTaskTriggerService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long WEBHOOK_WINDOW_MS = 5 * 60 * 1000L;
    private static final Set<String> TRIGGER_TYPES = Set.of("CRON", "EVENT", "MANUAL");
    private static final Set<String> CONCURRENCY_POLICIES = Set.of("FORBID", "ALLOW");
    private static final Set<String> MISSED_POLICIES = Set.of("SKIP", "FIRE_ONCE");

    @Resource
    private AgentTaskTriggerMapper mapper;
    @Resource
    private AgentProjectService agentProjectService;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private AgentExecutionService executionService;
    @Resource
    private AgentExecLogService execLogService;

    @Transactional(rollbackFor = Exception.class)
    public AgentTaskTriggerDTO create(AgentTaskTriggerRequest request) {
        String projectId = agentProjectService.resolveProjectId(request.getProjectId());
        Project project = projectMapper.selectByPrimaryKey(projectId);
        if (project == null) {
            throw new MSException("项目不存在: " + projectId);
        }
        String userId = requireUserId();
        long now = System.currentTimeMillis();
        AgentTaskTriggerDTO trigger = fromRequest(request, projectId, userId, now);
        trigger.setId(IDGenerator.nextStr());
        trigger.setOrganizationId(project.getOrganizationId());
        trigger.setCreatedBy(userId);
        trigger.setCreatedAt(now);
        trigger.setVersion(0);
        String secret = null;
        if ("EVENT".equals(trigger.getTriggerType())) {
            secret = newSecret();
            trigger.setWebhookSecretCipher(EncryptUtils.aesEncrypt(secret));
        }
        mapper.insert(trigger);
        execLogService.audit("AI_TASK_TRIGGER_CREATE", trigger.getId(), JSON.toJSONString(Map.of(
                "projectId", projectId, "type", trigger.getTriggerType(), "name", trigger.getName())));
        AgentTaskTriggerDTO response = sanitize(trigger);
        response.setWebhookSecret(secret);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentTaskTriggerDTO update(String id, AgentTaskTriggerRequest request) {
        AgentTaskTriggerDTO existing = requireAccessible(id);
        String projectId = agentProjectService.resolveProjectId(request.getProjectId());
        if (!StringUtils.equals(projectId, existing.getProjectId())) {
            throw new MSException("触发器不允许跨项目迁移");
        }
        long now = System.currentTimeMillis();
        AgentTaskTriggerDTO update = fromRequest(request, projectId, requireUserId(), now);
        update.setId(id);
        update.setVersion(existing.getVersion());
        update.setWebhookSecretCipher(null);
        if (mapper.update(update) != 1) {
            throw new MSException("触发器已被修改，请刷新后重试");
        }
        execLogService.audit("AI_TASK_TRIGGER_UPDATE", id, JSON.toJSONString(Map.of(
                "enabled", update.getEnabled(), "type", update.getTriggerType())));
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentTaskTriggerDTO rotateSecret(String id) {
        AgentTaskTriggerDTO existing = requireAccessible(id);
        if (!"EVENT".equals(existing.getTriggerType())) {
            throw new MSException("仅 EVENT 触发器支持 Webhook 密钥");
        }
        String secret = newSecret();
        existing.setWebhookSecretCipher(EncryptUtils.aesEncrypt(secret));
        existing.setUpdatedBy(requireUserId());
        existing.setUpdatedAt(System.currentTimeMillis());
        if (mapper.update(existing) != 1) {
            throw new MSException("触发器已被修改，请刷新后重试");
        }
        execLogService.audit("AI_TASK_TRIGGER_SECRET_ROTATE", id, "webhook secret rotated");
        AgentTaskTriggerDTO response = get(id);
        response.setWebhookSecret(secret);
        return response;
    }

    public AgentTaskTriggerDTO get(String id) {
        return sanitize(requireAccessible(id));
    }

    public List<AgentTaskTriggerDTO> list(String projectId) {
        String resolved = agentProjectService.resolveProjectId(projectId);
        return mapper.selectByProject(resolved).stream().map(this::sanitize).toList();
    }

    public List<AgentTaskTriggerHistoryDTO> history(String id, Integer limit) {
        requireAccessible(id);
        return mapper.selectHistory(id, Math.min(Math.max(limit == null ? 50 : limit, 1), 200));
    }

    public AgentTaskTriggerHistoryDTO manualFire(String id) {
        AgentTaskTriggerDTO trigger = requireAccessible(id);
        return fire(trigger, "manual-" + IDGenerator.nextStr(), null, null);
    }

    public AgentTaskTriggerHistoryDTO webhook(String id, String eventId, String timestamp,
                                              String signature, String rawBody) {
        AgentTaskTriggerDTO trigger = mapper.selectById(id);
        if (trigger == null || !Boolean.TRUE.equals(trigger.getEnabled()) || !"EVENT".equals(trigger.getTriggerType())) {
            throw new MSException("WEBHOOK_TRIGGER_NOT_FOUND");
        }
        validateWebhook(trigger, eventId, timestamp, signature, rawBody);
        AgentTaskTriggerHistoryDTO existing = mapper.selectHistoryByEvent(id, eventId);
        if (existing != null) {
            return existing;
        }
        AgentTaskTriggerEventRequest event;
        try {
            event = JSON.parseObject(rawBody, AgentTaskTriggerEventRequest.class);
        } catch (Exception ex) {
            throw new MSException("WEBHOOK_BODY_INVALID");
        }
        if (!StringUtils.equalsIgnoreCase(trigger.getEventType(), event.getEventType())) {
            return history(trigger, null, eventId, null, "SKIPPED", "EVENT_TYPE_MISMATCH");
        }
        if (!matchesFilter(trigger.getEventFilter(), event.getPayload())) {
            return history(trigger, null, eventId, null, "SKIPPED", "EVENT_FILTER_MISMATCH");
        }
        return fire(trigger, eventId, null, event.getPayload());
    }

    @Scheduled(fixedDelay = 30_000L)
    public void fireDueCronTriggers() {
        long now = System.currentTimeMillis();
        for (AgentTaskTriggerDTO trigger : mapper.selectDue(now, 100)) {
            long scheduledAt = trigger.getNextFireAt();
            boolean missed = now - scheduledAt > 60_000L;
            Long next = nextFire(trigger.getCronExpression(), trigger.getTimezone(), missed ? now : scheduledAt);
            if (mapper.claimScheduledFire(trigger.getId(), trigger.getVersion() == null ? 0 : trigger.getVersion(),
                    scheduledAt, next, now) != 1) {
                continue;
            }
            if ("SKIP".equals(trigger.getMissedPolicy()) && missed) {
                history(trigger, null, null, scheduledAt, "SKIPPED", "MISSED_SCHEDULE");
                continue;
            }
            fire(trigger, "schedule-" + scheduledAt, scheduledAt, null);
        }
    }

    private AgentTaskTriggerHistoryDTO fire(AgentTaskTriggerDTO trigger, String eventId,
                                            Long scheduledAt, Map<String, Object> payload) {
        long now = System.currentTimeMillis();
        if ("FORBID".equals(trigger.getConcurrencyPolicy()) && mapper.countActiveTasks(trigger.getId()) > 0) {
            return history(trigger, null, eventId, scheduledAt, "SKIPPED", "ACTIVE_TASK_EXISTS");
        }
        try {
            AgentExecutionCreateRequest request = JSON.parseObject(trigger.getTaskTemplate(), AgentExecutionCreateRequest.class);
            request.setProjectId(trigger.getProjectId());
            request.setSource("TRIGGER:" + trigger.getId());
            request.setIdempotencyKey("trigger:" + trigger.getId() + ":" + eventId);
            if (payload != null && !payload.isEmpty()) {
                request.setResolvedFilter(JSON.toJSONString(Map.of("triggerPayload", payload)));
            }
            AgentExecutionActorContext.bind(trigger.getCreatedBy());
            SessionUtils.setCurrentProjectId(trigger.getProjectId());
            SessionUtils.setCurrentOrganizationId(trigger.getOrganizationId());
            AgentExecutionTaskDTO task = executionService.create(request);
            mapper.updateFireResult(trigger.getId(), "CREATED", null, now);
            return history(trigger, task.getId(), eventId, scheduledAt, "CREATED", null);
        } catch (Exception ex) {
            String message = StringUtils.abbreviate(StringUtils.defaultIfBlank(ex.getMessage(), ex.getClass().getSimpleName()), 1000);
            mapper.updateFireResult(trigger.getId(), "FAILED", message, now);
            return history(trigger, null, eventId, scheduledAt, "FAILED", message);
        } finally {
            AgentExecutionActorContext.clear();
            SessionUtils.clearCurrentProjectId();
            SessionUtils.clearCurrentOrganizationId();
        }
    }

    private AgentTaskTriggerDTO fromRequest(AgentTaskTriggerRequest source, String projectId, String userId, long now) {
        AgentTaskTriggerDTO trigger = new AgentTaskTriggerDTO();
        trigger.setProjectId(projectId);
        trigger.setName(StringUtils.trim(source.getName()));
        trigger.setTriggerType(StringUtils.upperCase(StringUtils.trim(source.getTriggerType())));
        if (!TRIGGER_TYPES.contains(trigger.getTriggerType())) {
            throw new MSException("triggerType 仅支持 CRON/EVENT/MANUAL");
        }
        trigger.setTimezone(StringUtils.defaultIfBlank(source.getTimezone(), "Asia/Shanghai"));
        validateTimezone(trigger.getTimezone());
        trigger.setCronExpression(StringUtils.trimToNull(source.getCronExpression()));
        trigger.setEventType(StringUtils.upperCase(StringUtils.trimToNull(source.getEventType())));
        trigger.setEventFilter(normalizeJson(source.getEventFilter()));
        trigger.setConcurrencyPolicy(normalizeEnum(source.getConcurrencyPolicy(), "FORBID", CONCURRENCY_POLICIES, "concurrencyPolicy"));
        trigger.setMissedPolicy(normalizeEnum(source.getMissedPolicy(), "FIRE_ONCE", MISSED_POLICIES, "missedPolicy"));
        trigger.setEnabled(source.getEnabled() == null || source.getEnabled());
        source.getTaskTemplate().setProjectId(projectId);
        source.getTaskTemplate().setIdempotencyKey(null);
        source.getTaskTemplate().setSource(null);
        trigger.setTaskTemplate(JSON.toJSONString(source.getTaskTemplate()));
        if ("CRON".equals(trigger.getTriggerType())) {
            if (StringUtils.isBlank(trigger.getCronExpression())) {
                throw new MSException("CRON 触发器必须提供 cronExpression");
            }
            trigger.setNextFireAt(trigger.getEnabled() ? nextFire(trigger.getCronExpression(), trigger.getTimezone(), now) : null);
        } else {
            trigger.setCronExpression(null);
            trigger.setNextFireAt(null);
        }
        if ("EVENT".equals(trigger.getTriggerType()) && StringUtils.isBlank(trigger.getEventType())) {
            throw new MSException("EVENT 触发器必须提供 eventType");
        }
        trigger.setUpdatedBy(userId);
        trigger.setUpdatedAt(now);
        return trigger;
    }

    private Long nextFire(String expression, String timezone, long after) {
        try {
            CronExpression cron = new CronExpression(expression);
            cron.setTimeZone(TimeZone.getTimeZone(timezone));
            Date next = cron.getNextValidTimeAfter(new Date(after));
            if (next == null) {
                throw new MSException("cronExpression 没有下一次执行时间");
            }
            return next.getTime();
        } catch (ParseException ex) {
            throw new MSException("cronExpression 无效: " + ex.getMessage());
        }
    }

    private void validateWebhook(AgentTaskTriggerDTO trigger, String eventId, String timestamp,
                                 String signature, String rawBody) {
        if (StringUtils.isAnyBlank(eventId, timestamp, signature, rawBody)) {
            throw new MSException("WEBHOOK_SIGNATURE_HEADERS_REQUIRED");
        }
        if (eventId.length() > 128) {
            throw new MSException("WEBHOOK_EVENT_ID_TOO_LONG");
        }
        long timestampMillis;
        try {
            long raw = Long.parseLong(timestamp);
            timestampMillis = raw < 10_000_000_000L ? raw * 1000L : raw;
        } catch (NumberFormatException ex) {
            throw new MSException("WEBHOOK_TIMESTAMP_INVALID");
        }
        if (Math.abs(System.currentTimeMillis() - timestampMillis) > WEBHOOK_WINDOW_MS) {
            throw new MSException("WEBHOOK_TIMESTAMP_EXPIRED");
        }
        String expected = hmacHex(EncryptUtils.aesDecrypt(trigger.getWebhookSecretCipher()),
                timestamp + "." + rawBody);
        String provided = StringUtils.removeStartIgnoreCase(signature.trim(), "sha256=");
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                provided.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new MSException("WEBHOOK_SIGNATURE_INVALID");
        }
    }

    private String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new MSException("WEBHOOK_SIGNATURE_ERROR");
        }
    }

    private AgentTaskTriggerHistoryDTO history(AgentTaskTriggerDTO trigger, String taskId, String eventId,
                                               Long scheduledAt, String status, String message) {
        AgentTaskTriggerHistoryDTO existing = StringUtils.isBlank(eventId)
                ? null : mapper.selectHistoryByEvent(trigger.getId(), eventId);
        if (existing != null) {
            return existing;
        }
        long now = System.currentTimeMillis();
        AgentTaskTriggerHistoryDTO history = new AgentTaskTriggerHistoryDTO();
        history.setId(IDGenerator.nextStr());
        history.setTriggerId(trigger.getId());
        history.setTaskId(taskId);
        history.setEventId(eventId);
        history.setScheduledAt(scheduledAt);
        history.setFireTime(now);
        history.setStatus(status);
        history.setMessage(StringUtils.abbreviate(message, 1000));
        history.setCreatedAt(now);
        try {
            mapper.insertHistory(history);
            return history;
        } catch (DuplicateKeyException ex) {
            AgentTaskTriggerHistoryDTO duplicate = mapper.selectHistoryByEvent(trigger.getId(), eventId);
            if (duplicate != null) {
                return duplicate;
            }
            throw ex;
        }
    }

    private boolean matchesFilter(String filterJson, Map<String, Object> payload) {
        if (StringUtils.isBlank(filterJson)) {
            return true;
        }
        Map<String, Object> filter = JSON.parseMap(filterJson);
        Map<String, Object> actual = payload == null ? Map.of() : payload;
        return filter.entrySet().stream().allMatch(entry ->
                StringUtils.equals(JSON.toJSONString(entry.getValue()), JSON.toJSONString(actual.get(entry.getKey()))));
    }

    private AgentTaskTriggerDTO requireAccessible(String id) {
        AgentTaskTriggerDTO trigger = mapper.selectById(id);
        if (trigger == null) {
            throw new MSException("触发器不存在: " + id);
        }
        String projectId = agentProjectService.resolveProjectId(trigger.getProjectId());
        if (!StringUtils.equals(projectId, trigger.getProjectId())) {
            throw new MSException("触发器项目校验失败");
        }
        return trigger;
    }

    private String requireUserId() {
        String userId = StringUtils.defaultIfBlank(SessionUtils.getUserId(), AgentExecutionActorContext.get());
        if (StringUtils.isBlank(userId)) {
            throw new MSException("无法解析当前用户");
        }
        return userId;
    }

    private AgentTaskTriggerDTO sanitize(AgentTaskTriggerDTO source) {
        AgentTaskTriggerDTO copy = JSON.parseObject(JSON.toJSONString(source), AgentTaskTriggerDTO.class);
        copy.setWebhookSecretCipher(null);
        copy.setWebhookSecret(null);
        return copy;
    }

    private String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "mstw_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeJson(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return JSON.toJSONString(JSON.parseObject(value));
        } catch (Exception ex) {
            throw new MSException("eventFilter 必须是合法 JSON");
        }
    }

    private String normalizeEnum(String value, String defaultValue, Set<String> allowed, String field) {
        String normalized = StringUtils.upperCase(StringUtils.defaultIfBlank(value, defaultValue));
        if (!allowed.contains(normalized)) {
            throw new MSException(field + " 取值无效");
        }
        return normalized;
    }

    private void validateTimezone(String timezone) {
        if (!Set.of(TimeZone.getAvailableIDs()).contains(timezone)) {
            throw new MSException("timezone 无效: " + timezone);
        }
    }
}
