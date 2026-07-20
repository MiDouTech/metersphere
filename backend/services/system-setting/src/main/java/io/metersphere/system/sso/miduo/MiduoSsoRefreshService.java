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
 * <p>
 * 策略：刚登录宽限期内不 refresh、不踢出；expiresAt 缺失时信任 Redis TTL；
 * refresh 失败且会话尚未真正过期时仅打日志，避免「登录闪成功后立刻踢回登录桥」。
 */
@Service
public class MiduoSsoRefreshService {

    private static final Logger log = LoggerFactory.getLogger(MiduoSsoRefreshService.class);
    /** 距过期不足该毫秒数且 refresh 失败时，才要求前端重登 */
    private static final long HARD_EXPIRE_LEFT_MS = 60_000L;

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

        String sessionToken = miduoSsoSessionStore.getSessionToken(userId);
        Long expiresAt = miduoSsoSessionStore.getExpiresAt(userId);
        long now = System.currentTimeMillis();
        long graceMs = Math.max(60L, properties.getLoginGraceSeconds()) * 1000L;
        Long savedAt = miduoSsoSessionStore.getSavedAt(userId);

        // 刚完成 callback 写入：宽限期内不续期、不踢出（避免并发 is-login 误杀）
        if (savedAt != null && now - savedAt < graceMs) {
            return false;
        }

        if (StringUtils.isBlank(sessionToken)) {
            return miduoSsoSessionStore.isNeedReauth(userId);
        }

        // 无绝对过期时间：依赖 Redis TTL，不主动 refresh，也不因历史 needReauth 误踢
        if (expiresAt == null) {
            if (miduoSsoSessionStore.isNeedReauth(userId)) {
                miduoSsoSessionStore.clearNeedReauth(userId);
            }
            return false;
        }

        long aheadMs = Math.max(60L, properties.getRefreshAheadSeconds()) * 1000L;
        boolean stillComfortable = expiresAt - now > aheadMs;
        if (stillComfortable) {
            if (miduoSsoSessionStore.isNeedReauth(userId)) {
                miduoSsoSessionStore.clearNeedReauth(userId);
            }
            return false;
        }

        try {
            MiduoRefreshResult result = miduoSsoClient.refreshSessionToken(sessionToken);
            miduoSsoSessionStore.save(userId, result.getSessionToken(), result.getExpiresAt());
            return false;
        } catch (Exception e) {
            log.warn("miduo refresh failed userId={} err={}", userId, e.getMessage());
            // 会话其实还有效：软失败，下次 is-login 再试，避免闪退后踢出
            if (expiresAt - now > HARD_EXPIRE_LEFT_MS) {
                return false;
            }
            miduoSsoSessionStore.markNeedReauth(userId);
            return true;
        }
    }
}
