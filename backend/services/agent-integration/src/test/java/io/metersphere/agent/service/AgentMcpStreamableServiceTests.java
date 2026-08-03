package io.metersphere.agent.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class AgentMcpStreamableServiceTests {

    private AgentMcpStreamableService service;

    @BeforeEach
    void setUp() {
        // isNotification / handleNotification 不依赖注入字段
        service = new AgentMcpStreamableService();
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
