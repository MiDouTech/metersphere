package io.metersphere.system.wecombot;

import io.metersphere.system.service.PlatformNotificationScope;
import io.metersphere.system.utils.SessionUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

class WecomBotPermissionAspectTests {
    @Test void permissionDenialPreventsControllerFromReadingConfiguration() {
        var scope=mock(PlatformNotificationScope.class);
        var service=mock(WecomBotService.class);
        var factory=new AspectJProxyFactory(new WecomBotController(service));
        factory.addAspect(new WecomBotPermissionAspect(scope));
        WecomBotController controller=factory.getProxy();
        doThrow(new UnauthorizedException("denied")).when(scope).requireSystemPermissions(eq("actor"),any(String[].class),eq(true));
        try(var session=mockStatic(SessionUtils.class)) {
            session.when(SessionUtils::getUserId).thenReturn("actor");
            assertThrows(UnauthorizedException.class,controller::config);
            verify(scope).requireSystemPermissions(eq("actor"),aryEq(new String[]{"SYSTEM_CONFIG_WECOM_BOT:READ"}),eq(true));
            verifyNoInteractions(service);
        }
    }
}
