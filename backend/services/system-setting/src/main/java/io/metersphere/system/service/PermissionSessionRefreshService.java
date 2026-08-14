package io.metersphere.system.service;

import io.metersphere.sdk.constants.SessionConstants;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.dto.user.UserDTO;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 权限变化后刷新所有在线会话中的权限快照。刷新失败时删除会话，避免继续使用旧权限。
 */
@Service
public class PermissionSessionRefreshService {

    private static final String SESSION_KEY_PREFIX = "spring:session:sessions:";
    private static final String USER_ATTRIBUTE_KEY = "sessionAttr:" + SessionConstants.ATTR_USER;

    @Resource
    private RedisIndexedSessionRepository sessionRepository;
    @Resource
    private UserLoginService userLoginService;

    public void refreshUsersAfterCommit(Collection<String> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return;
        }
        Set<String> distinctUserIds = new LinkedHashSet<>(userIds);
        Runnable task = () -> distinctUserIds.forEach(this::refreshUserSessionsFailClosed);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void refreshUserSessionsFailClosed(String userId) {
        try {
            UserDTO user = userLoginService.getUserDTO(userId);
            if (user == null) {
                SessionUtils.kickOutUser(userId);
                return;
            }
            Map<String, ?> sessions = sessionRepository.findByPrincipalName(userId);
            for (Map.Entry<String, ?> entry : sessions.entrySet()) {
                String sessionId = entry.getKey();
                SessionUser refreshed = SessionUser.fromUser(user, sessionId);
                if (entry.getValue() instanceof Session session) {
                    SessionUser old = session.getAttribute(SessionConstants.ATTR_USER);
                    if (old != null) {
                        refreshed.setCsrfToken(old.getCsrfToken());
                        refreshed.setNeedMiduoReauth(old.getNeedMiduoReauth());
                    }
                }
                sessionRepository.getSessionRedisOperations().opsForHash()
                        .put(SESSION_KEY_PREFIX + sessionId, USER_ATTRIBUTE_KEY, refreshed);
            }
        } catch (Exception e) {
            LogUtils.error("刷新用户权限会话失败，已按失败关闭删除会话，userId=" + userId, e);
            SessionUtils.kickOutUser(userId);
        }
    }
}
