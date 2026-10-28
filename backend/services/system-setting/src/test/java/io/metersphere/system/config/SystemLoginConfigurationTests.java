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
    @Test void standaloneModuleWithoutResourceAdaptersStillStarts() {
        new ApplicationContextRunner().withUserConfiguration(io.metersphere.system.edit.ResourceEditAdapterRegistry.class)
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertNull(context.getBean(io.metersphere.system.edit.ResourceEditAdapterRegistry.class).get("uninstalled"));
            });
    }
    @Test void installedResourceAdapterIsStillRegistered() {
        var adapter=org.mockito.Mockito.mock(io.metersphere.system.edit.ResourceEditAdapter.class);
        org.mockito.Mockito.when(adapter.resourceType()).thenReturn("FUNCTIONAL_CASE");
        new ApplicationContextRunner().withUserConfiguration(io.metersphere.system.edit.ResourceEditAdapterRegistry.class)
            .withBean("testAdapter", io.metersphere.system.edit.ResourceEditAdapter.class, () -> adapter)
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertSame(adapter,context.getBean(io.metersphere.system.edit.ResourceEditAdapterRegistry.class).get("FUNCTIONAL_CASE"));
            });
    }
}
