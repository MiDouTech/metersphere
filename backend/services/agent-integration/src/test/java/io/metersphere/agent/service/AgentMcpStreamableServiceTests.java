package io.metersphere.agent.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

class AgentMcpStreamableServiceTests {

    private AgentMcpStreamableService service;

    @BeforeEach
    void setUp() {
        // isNotification / handleNotification 不依赖注入字段
        service = new AgentMcpStreamableService();
        ReflectionTestUtils.setField(service, "safeErrorMapper", new AgentSafeErrorMapper());
    }

    @Test
    void directTriggerToolCallIsAlwaysForbiddenForPersonalMcp() {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", 7);
        request.put("method", "tools/call");
        request.put("params", Map.of("name", "metersphere.execution.trigger.fire", "arguments", Map.of()));
        Map<String, Object> response = service.handle(request);
        Map<?, ?> error = (Map<?, ?>) response.get("error");
        Map<?, ?> data = (Map<?, ?>) error.get("data");
        Assertions.assertEquals("MCP_TOOL_FORBIDDEN", data.get("code"));
    }

    @Test
    void notificationWithoutIdShouldBeDetected() {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "notifications/initialized");
        Assertions.assertTrue(service.isNotification(request));
    }

    @Test
    void requestWithIdShouldNotBeNotification() {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "notifications/initialized");
        Assertions.assertFalse(service.isNotification(request));
    }

    @Test
    void handleNotificationInitializedShouldNoop() {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "notifications/initialized");
        Assertions.assertDoesNotThrow(() -> service.handleNotification(request));
    }
}
