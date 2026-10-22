package io.metersphere.agent.security;

import io.metersphere.agent.constants.AgentConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 原子滑动窗口，在多个应用副本之间共享 Agent Token 配额。
 */
@Component
public class AgentTokenRateLimiter {
    @jakarta.annotation.Resource
    private org.springframework.data.redis.core.StringRedisTemplate redis;
    @org.springframework.beans.factory.annotation.Value("${agent.rate-limit.namespace:metersphere}")
    private String namespace = "metersphere";
    @org.springframework.beans.factory.annotation.Value("${agent.rate-limit.per-token:720}")
    private int tokenLimit = 720;
    @org.springframework.beans.factory.annotation.Value("${agent.rate-limit.window-ms:60000}")
    private long windowMs = 60000;
    private final java.util.function.LongSupplier clock;
    public AgentTokenRateLimiter() { this(System::currentTimeMillis); }
    AgentTokenRateLimiter(java.util.function.LongSupplier clock) { this.clock = clock; }
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> ADMIT =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>("""
                local time = redis.call('TIME')
                local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
                local window = tonumber(ARGV[1])
                redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now-window)
                if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[2]) then return 0 end
                local last = redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES')
                if #last > 0 and now-tonumber(last[2]) < tonumber(ARGV[4]) then return 0 end
                redis.call('ZADD', KEYS[1], now, ARGV[3])
                redis.call('PEXPIRE', KEYS[1], window)
                return 1
                """, Long.class);

    @jakarta.annotation.PostConstruct
    public void validateConfiguration() {
        if (tokenLimit <= 0 || windowMs <= 0 || StringUtils.isBlank(namespace)) {
            throw new IllegalArgumentException("Invalid Agent rate limit configuration");
        }
    }

    private boolean shared(String dimension, String key, int limit, long interval) {
        String hash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(key);
        Long result = redis.execute(ADMIT, java.util.List.of(namespace + ":agent-rate:" + dimension + ":" + hash),
                String.valueOf(windowMs), String.valueOf(limit), java.util.UUID.randomUUID().toString(), String.valueOf(interval));
        if (result == null) throw new org.springframework.dao.DataAccessResourceFailureException("Rate limit store unavailable");
        return result == 1;
    }
    private final ConcurrentHashMap<String, ArrayDeque<Long>> generalWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ArrayDeque<Long>> searchWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ArrayDeque<Long>> toolWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ArrayDeque<Long>> ipWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastSearchAt = new ConcurrentHashMap<>();

    public boolean tryAcquire(String tokenId, boolean searchApi) {
        if (StringUtils.isBlank(tokenId)) {
            return true;
        }
        // Runtime injection is required. The local path is only used by standalone clock-based unit tests.
        if (redis != null) {
            if (!shared("total", tokenId, tokenLimit, 0)) return false;
            return !searchApi || shared("search", tokenId, AgentConstants.SEARCH_RATE_LIMIT_PER_MINUTE, AgentConstants.SEARCH_MIN_INTERVAL_MS);
        }
        long now = clock.getAsLong();
        if (!allow(generalWindows, tokenId, now, tokenLimit)) {
            return false;
        }
        if (!searchApi) {
            return true;
        }
        Long last = lastSearchAt.get(tokenId);
        if (last != null && now - last < AgentConstants.SEARCH_MIN_INTERVAL_MS) {
            return false;
        }
        if (!allow(searchWindows, tokenId, now, AgentConstants.SEARCH_RATE_LIMIT_PER_MINUTE)) {
            return false;
        }
        lastSearchAt.put(tokenId, now);
        return true;
    }

    public boolean tryAcquireIp(String tokenId, String ip) {
        if (StringUtils.isAnyBlank(tokenId, ip)) {
            return true;
        }
        if (redis != null) return shared("ip", tokenId + ":" + ip, tokenLimit, 0);
        return allow(ipWindows, tokenId + ":" + ip, clock.getAsLong(), tokenLimit);
    }

    public boolean tryAcquireTool(String tokenId, String toolName) {
        if (StringUtils.isAnyBlank(tokenId, toolName)) {
            return true;
        }
        if (redis != null) return shared("tool", tokenId + ":" + toolName, tokenLimit, 0);
        return allow(toolWindows, tokenId + ":" + toolName, clock.getAsLong(), tokenLimit);
    }

    public static boolean isSearchApi(String requestUri) {
        if (StringUtils.isBlank(requestUri)) {
            return false;
        }
        String path = requestUri;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        return path.endsWith("/search")
                || path.contains("/agent/v1/functional/search")
                || path.contains("/agent/v1/bug/search");
    }

    private boolean allow(Map<String, ArrayDeque<Long>> store, String key, long now, int limit) {
        ArrayDeque<Long> window = store.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            long cutoff = now - windowMs;
            Iterator<Long> it = window.iterator();
            while (it.hasNext()) {
                if (it.next() <= cutoff) {
                    it.remove();
                } else {
                    break;
                }
            }
            if (window.size() >= limit) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
