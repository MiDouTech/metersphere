package io.metersphere.agent.security;

import io.metersphere.agent.constants.AgentConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 Agent Token 的进程内滑动窗口限流，防止无节制轮询检索打满 DB。
 */
@Component
public class AgentTokenRateLimiter {
    private final ConcurrentHashMap<String, ArrayDeque<Long>> generalWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ArrayDeque<Long>> searchWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastSearchAt = new ConcurrentHashMap<>();

    public boolean tryAcquire(String tokenId, boolean searchApi) {
        if (StringUtils.isBlank(tokenId)) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (!allow(generalWindows, tokenId, now, AgentConstants.RATE_LIMIT_PER_MINUTE)) {
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
            long cutoff = now - AgentConstants.RATE_LIMIT_WINDOW_MS;
            Iterator<Long> it = window.iterator();
            while (it.hasNext()) {
                if (it.next() < cutoff) {
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
