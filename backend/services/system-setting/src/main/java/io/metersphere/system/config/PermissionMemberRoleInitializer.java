package io.metersphere.system.config;

import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.service.PermissionControlService;
import jakarta.annotation.Resource;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PermissionMemberRoleInitializer {
    @Resource
    private PermissionControlService permissionControlService;

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeMemberPermissions() {
        try {
            permissionControlService.synchronizeMemberRolePermissions();
        } catch (Exception e) {
            LogUtils.error("同步成员角色完整权限失败", e);
            throw new IllegalStateException("同步成员角色完整权限失败", e);
        }
    }
}
