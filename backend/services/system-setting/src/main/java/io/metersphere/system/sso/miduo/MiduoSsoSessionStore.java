package io.metersphere.system.sso.miduo;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.config.MiduoSsoProperties;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 米多 sessionToken 服务端存储（仅 Redis，禁止下发浏览器）。
 */
@Service
public class MiduoSsoSessionStore {

    private static final String KEY_PREFIX = "miduo:sso:session:v2:";
    @org.springframework.beans.factory.annotation.Value("${spring.session.redis.namespace:spring:session}")
    private String instance = "spring:session";

    private String userIndex(String userId) { return KEY_PREFIX + instance + ":user:" + userId; }

    private String sessionKey(String userId) {
        var session = org.apache.shiro.SecurityUtils.getSubject().getSession(false);
        if (session == null || !userId.equals(io.metersphere.system.utils.SessionUtils.getUserId())) {
            throw new IllegalStateException("SSO session ownership is unavailable");
        }
        return KEY_PREFIX + instance + ":" + userId + ":" + session.getId();
    }

    public void revokeUser(String userId) {
        var keys = stringRedisTemplate.opsForSet().members(userIndex(userId));
        if (keys != null && !keys.isEmpty()) stringRedisTemplate.delete(keys);
        stringRedisTemplate.delete(userIndex(userId));
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private MiduoSsoProperties properties;

    public void save(String userId, String sessionToken, Long expiresAtMillis) {
        if (StringUtils.isAnyBlank(userId, sessionToken)) {
            return;
        }
        Map<String, Object> value = new HashMap<>();
        value.put("sessionToken", sessionToken);
        value.put("expiresAt", expiresAtMillis);
        value.put("savedAt", System.currentTimeMillis());
        value.put("needReauth", false);
        stringRedisTemplate.opsForSet().add(userIndex(userId), sessionKey(userId));
        stringRedisTemplate.expire(userIndex(userId), Duration.ofDays(14));
        long ttl = resolveTtlSeconds(expiresAtMillis);
        stringRedisTemplate.opsForValue().set(sessionKey(userId), JSON.toJSONString(value), Duration.ofSeconds(ttl));
    }

    public String getSessionToken(String userId) {
        Map<String, Object> data = load(userId);
        if (data == null) {
            return null;
        }
        Object token = data.get("sessionToken");
        return token == null ? null : String.valueOf(token);
    }

    public Long getExpiresAt(String userId) {
        Map<String, Object> data = load(userId);
        if (data == null || data.get("expiresAt") == null) {
            return null;
        }
        return MiduoSsoClient.asEpochMillis(data.get("expiresAt"));
    }

    public Long getSavedAt(String userId) {
        Map<String, Object> data = load(userId);
        if (data == null || data.get("savedAt") == null) {
            return null;
        }
        try {
            Object v = data.get("savedAt");
            if (v instanceof Number num) {
                return num.longValue();
            }
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isNeedReauth(String userId) {
        Map<String, Object> data = load(userId);
        if (data == null) {
            return true; // Legacy user-key credentials cannot be assigned to an individual session.
        }
        Object flag = data.get("needReauth");
        return Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag));
    }

    public void markNeedReauth(String userId) {
        Map<String, Object> data = load(userId);
        if (data == null) {
            data = new HashMap<>();
        }
        data.put("needReauth", true);
        long ttl = Math.max(60, properties.getSessionTtlSeconds());
        stringRedisTemplate.opsForValue().set(sessionKey(userId), JSON.toJSONString(data), Duration.ofSeconds(ttl));
    }

    public void clearNeedReauth(String userId) {
        Map<String, Object> data = load(userId);
        if (data == null) {
            return;
        }
        data.put("needReauth", false);
        long ttl = resolveTtlSeconds(getExpiresAt(userId));
        stringRedisTemplate.opsForValue().set(sessionKey(userId), JSON.toJSONString(data), Duration.ofSeconds(ttl));
    }

    public void delete(String userId) {
        if (StringUtils.isBlank(userId)) {
            return;
        }
        String key = sessionKey(userId);
        stringRedisTemplate.delete(key);
        stringRedisTemplate.opsForSet().remove(userIndex(userId), key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> load(String userId) {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(sessionKey(userId));
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return JSON.parseObject(json, Map.class);
    }

    private long resolveTtlSeconds(Long expiresAtMillis) {
        long defaultTtl = Math.max(60, properties.getSessionTtlSeconds());
        if (expiresAtMillis == null || expiresAtMillis <= 0) {
            return defaultTtl;
        }
        long remain = (expiresAtMillis - System.currentTimeMillis()) / 1000;
        if (remain <= 0) {
            return 60;
        }
        return Math.min(defaultTtl, remain);
    }
}
