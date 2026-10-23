package io.metersphere.functional.service;

import io.metersphere.sdk.constants.SessionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.security.SessionLifetime;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

/** Short-lived notification credentials; never a download credential. */
@Service
public class CaseExportNotificationTickets {
    public static final String PROTOCOL = "ms-case-export-v1";
    public static final String PURPOSE = "CASE_EXPORT_NOTIFY";
    public static final int TTL_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    @Resource private StringRedisTemplate redis;
    @Resource private SessionRepository<? extends Session> sessions;
    @Resource private FunctionalCaseExportTaskService tasks;
    @Value("${case.export.websocket.enabled:false}") private boolean enabled;
    @Value("${case.export.websocket.allowed-origins:}") private String configuredOrigins = "";
    @Value("${spring.session.redis.namespace:spring:session}") private String namespace = "spring:session";
    private Set<String> origins = Set.of();

    public record Claims(String userId, String projectId, String taskId, String sessionId, String origin, String purpose) { }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Ticket(boolean enabled, String ticket, String taskId, int expiresInSeconds, String protocol) { }
    public static final class TicketException extends MSException {
        TicketException(MsHttpResultCode code, String message) { super(code, message); }
        public int status() { return getErrorCode().getCode() % 1000; }
    }
    private static final DefaultRedisScript<Long> RATE = new DefaultRedisScript<>("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('EXPIRE', KEYS[1], 60) end
            return n
            """, Long.class);
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return nil end
            local claim = cjson.decode(value)
            if claim.taskId ~= ARGV[1] or claim.origin ~= ARGV[2] or claim.purpose ~= 'CASE_EXPORT_NOTIFY' then return nil end
            redis.call('DEL', KEYS[1])
            return value
            """, String.class);

    @PostConstruct
    public void validateConfiguration() {
        if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("Notification namespace is required");
        Set<String> allowed = new LinkedHashSet<>();
        for (String value : configuredOrigins.split(",")) if (!value.isBlank()) allowed.add(normalizeOrigin(value.trim()));
        if (allowed.size() > 16 || (enabled && allowed.isEmpty()))
            throw new IllegalArgumentException("Enabled export notifications require explicit allowed origins (at most 16)");
        origins = Set.copyOf(allowed);
    }

    public Set<String> allowedOrigins() { return origins; }

    public Ticket issue(String taskId, String userId, String sessionId, String origin) {
        var task = tasks.get(taskId, userId);
        if (!enabled) return new Ticket(false, null, task.getTaskId(), 0, PROTOCOL);
        String acceptedOrigin = requireOrigin(origin);
        Claims claims = new Claims(userId, task.getProjectId(), task.getTaskId(), sessionId, acceptedOrigin, PURPOSE);
        validateSession(claims);
        try {
            Long count = redis.execute(RATE, List.of(namespace + ":case-export-ws:rate:" + DigestUtils.sha256Hex(userId)));
            if (count == null) throw unavailable();
            if (count > 30) throw new TicketException(MsHttpResultCode.TOO_MANY_REQUESTS, "通知连接申请过于频繁，请稍后重试；导出状态仍可刷新查看");
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(ticket), JSON.toJSONString(claims), Duration.ofSeconds(TTL_SECONDS))))
                throw unavailable();
            return new Ticket(true, ticket, task.getTaskId(), TTL_SECONDS, PROTOCOL);
        } catch (TicketException exception) { throw exception; }
        catch (RuntimeException storeFailure) { throw unavailable(); }
    }

    public Claims consume(String ticket, String taskId, String origin) {
        if (!enabled || ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")
                || taskId == null || !taskId.matches("[A-Za-z0-9_-]{1,64}")) throw denied();
        String acceptedOrigin = requireOrigin(origin);
        String value;
        try { value = redis.execute(CONSUME, List.of(key(ticket)), taskId, acceptedOrigin); }
        catch (RuntimeException storeFailure) { throw unavailable(); }
        if (value == null) throw denied();
        Claims claims;
        try { claims = JSON.parseObject(value, Claims.class); }
        catch (RuntimeException invalid) { throw denied(); }
        if (claims == null || !PURPOSE.equals(claims.purpose()) || !taskId.equals(claims.taskId())
                || !acceptedOrigin.equals(claims.origin())) throw denied();
        validateSession(claims);
        try {
            var task = tasks.get(claims.taskId(), claims.userId());
            if (!Objects.equals(task.getProjectId(), claims.projectId())) throw denied();
        } catch (TicketException exception) { throw exception; }
        catch (MSException denied) { throw denied(); }
        return claims;
    }

    public void validateSession(Claims claims) {
        if (claims == null || claims.sessionId() == null || claims.sessionId().isBlank()) throw denied();
        Session session;
        try { session = sessions.findById(claims.sessionId()); }
        catch (RuntimeException storeFailure) { throw unavailable(); }
        if (session == null || session.isExpired()) throw denied();
        Object user = session.getAttribute(SessionConstants.ATTR_USER);
        Object expiry = session.getAttribute(SessionLifetime.EXPIRES_AT);
        long absoluteExpiry = expiry instanceof Number value ? value.longValue()
                : session.getCreationTime().toEpochMilli() + SessionLifetime.MAX_AGE_MS;
        if (!(user instanceof SessionUser owner) || !Objects.equals(owner.getId(), claims.userId())
                || System.currentTimeMillis() >= absoluteExpiry) throw denied();
    }

    private String key(String ticket) { return namespace + ":case-export-ws:ticket:" + DigestUtils.sha256Hex(ticket); }
    private String requireOrigin(String origin) {
        String normalized;
        try { normalized = normalizeOrigin(origin); }
        catch (RuntimeException invalid) { throw denied(); }
        if (!origins.contains(normalized)) throw denied();
        return normalized;
    }
    private static String normalizeOrigin(String origin) {
        if (origin == null || origin.length() > 200) throw new IllegalArgumentException("Invalid origin");
        URI uri = URI.create(origin);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getPort() == 0 || uri.getPort() > 65535
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())))
            throw new IllegalArgumentException("Invalid origin");
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getRawAuthority().toLowerCase(Locale.ROOT);
    }
    public static TicketException denied() {
        return new TicketException(MsHttpResultCode.FORBIDDEN, "通知连接验证失败，请重新申请连接；导出状态仍可刷新查看");
    }
    public static TicketException unavailable() {
        return new TicketException(MsHttpResultCode.SERVICE_UNAVAILABLE, "通知连接暂不可用，请刷新查看导出状态");
    }
}
