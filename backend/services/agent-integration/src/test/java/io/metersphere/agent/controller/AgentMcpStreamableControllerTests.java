package io.metersphere.agent.controller;

import io.metersphere.system.controller.handler.annotation.NoResultHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

class AgentMcpStreamableControllerTests {

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
