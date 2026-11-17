package io.metersphere.agent.service;

import io.metersphere.agent.tool.BugImageReadTool;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BugImageRollbackTests {
    ApplicationContextRunner context() {
        return new ApplicationContextRunner().withBean(AgentBugImageService.class,()->mock(AgentBugImageService.class))
                .withUserConfiguration(BugImageReadTool.class);
    }
    @Test void toolIsRegisteredByDefault() { context().run(context->assertEquals(1,context.getBeanNamesForType(BugImageReadTool.class).length)); }
    @Test void disablingCapabilityRemovesTheToolFromTheActualSpringRegistry() {
        context().withPropertyValues("agent.bug-images.enabled=false").run(context->assertEquals(0,context.getBeanNamesForType(BugImageReadTool.class).length));
    }
}
