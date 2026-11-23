package io.metersphere.agent.controller;

import io.metersphere.system.controller.handler.annotation.NoResultHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

class AgentMcpStreamableControllerTests {
    @Test
    void mcpResponsesContainingConfigurationValuesMustNotBeCached() {
        var service = org.mockito.Mockito.mock(io.metersphere.agent.service.AgentMcpStreamableService.class);
        var controller = new AgentMcpStreamableController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "agentMcpStreamableService", service);
        var response = controller.post(Map.of("id", 1, "method", "tools/call"),
                org.mockito.Mockito.mock(HttpServletRequest.class));
        Assertions.assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    void mcpEndpointsShouldBypassResultHolderWrapping() throws NoSuchMethodException {
        assertNoResultHolder("post", Map.class, HttpServletRequest.class);
        assertNoResultHolder("get");
        assertNoResultHolder("delete");
    }

    private void assertNoResultHolder(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = AgentMcpStreamableController.class.getDeclaredMethod(methodName, parameterTypes);
        Assertions.assertTrue(method.isAnnotationPresent(NoResultHolder.class));
    }
}
