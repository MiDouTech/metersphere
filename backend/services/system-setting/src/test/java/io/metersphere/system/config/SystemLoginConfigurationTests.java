package io.metersphere.system.config;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
class SystemLoginConfigurationTests {
    @Test void standaloneModuleRegistersAndBindsBothLoginProperties() {
        new ApplicationContextRunner().withUserConfiguration(SystemLoginConfiguration.class)
            .withPropertyValues("metersphere.local-login.enabled=false")
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertEquals(1,context.getBeansOfType(LocalLoginProperties.class).size());
                assertFalse(context.getBean(LocalLoginProperties.class).isEnabled());
                assertEquals(1,context.getBeansOfType(MiduoSsoProperties.class).size());
            });
    }
}
