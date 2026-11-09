package io.metersphere.system.wecombot;

import io.metersphere.system.service.PlatformNotificationScope;
import io.metersphere.system.utils.SessionUtils;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Reuse each endpoint's existing permission, without project/organization admin bypasses. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class WecomBotPermissionAspect {
    private final PlatformNotificationScope scope;
    public WecomBotPermissionAspect(PlatformNotificationScope scope) { this.scope=scope; }

    @Before("execution(public * io.metersphere.system.wecombot.WecomBotController.*(..)) && @annotation(permission)")
    public void requireSystemPermission(RequiresPermissions permission) {
        scope.requireSystemPermissions(SessionUtils.getUserId(),permission.value(),permission.logical()==Logical.AND);
    }
}
