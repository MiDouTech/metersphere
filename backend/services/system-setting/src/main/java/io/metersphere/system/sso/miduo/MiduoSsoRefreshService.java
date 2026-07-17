package io.metersphere.system.sso.miduo;

import io.metersphere.system.config.MiduoSsoProperties;
import io.metersphere.system.dto.sso.miduo.MiduoRefreshResult;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 米多 sessionToken 续期（后端触发）。
 * success=false 或异常时标记 needReauth，前端跳登录桥。
 */
@Service
public class MiduoSsoRefreshService {

    private static final Logger log = LoggerFactory.getLogger(MiduoSsoRefreshService.class);

    @Resource
    private MiduoSsoProperties properties;
    @Resource
    private MiduoSsoClient miduoSsoClient;
    @Resource
    private MiduoSsoSessionStore miduoSsoSessionStore;

    /**
     * @return true 若需要前端走登录桥重认证
     */
    public boolean refreshIfNeeded(String userId) {
        if (StringUtils.isBlank(userId) || !properties.isConfigured()) {
            return false;
        }
        if (miduoSsoSessionStore.isNeedReauth(userId)) {
            return true;
        }
        String sessionToken = miduoSsoSessionStore.getSessionToken(userId);
        if (StringUtils.isBlank(sessionToken)) {
            return false;
        }
        Long expiresAt = miduoSsoSessionStore.getExpiresAt(userId);
        long aheadMs = Math.max(60, properties.getRefreshAheadSeconds()) * 1000L;
        if (expiresAt != null && expiresAt - System.currentTimeMillis() > aheadMs) {
            return false;
        }
        try {
            MiduoRefreshResult result = miduoSsoClient.refreshSessionToken(sessionToken);
            miduoSsoSessionStore.save(userId, result.getSessionToken(), result.getExpiresAt());
            return false;
        } catch (Exception e) {
            log.warn("miduo refresh failed userId={} err={}", userId, e.getMessage());
            miduoSsoSessionStore.markNeedReauth(userId);
            return true;
        }
    }
}
