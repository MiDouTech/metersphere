package io.metersphere.system.service.ai.agent.bridge;

import io.metersphere.system.service.ai.agent.AiUserAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentBridgeHandshakeInterceptorTests {
    @Mock private AiUserAgentService service;
    @Mock private ServerHttpRequest request;
    @Mock private ServerHttpResponse response;
    @Mock private WebSocketHandler handler;
    private AgentBridgeHandshakeInterceptor interceptor;
    private HttpHeaders headers;

    @BeforeEach
    void setup() {
        interceptor = new AgentBridgeHandshakeInterceptor(service);
        headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
    }

    @Test
    void acceptsAuthenticatedCompatibleBridge() {
        headers.set("X-Agent-Device-Id", "device-1");
        headers.set("X-Agent-Protocol-Version", "1.0");
        headers.set("X-Agent-Bridge-Version", "0.1.0");
        headers.setBearerAuth("short-token");
        when(service.authenticateToken("device-1", "short-token"))
                .thenReturn(Map.of("user_id", "user-1", "protocol_version", "1.0"));

        Map<String, Object> attributes = new HashMap<>();
        assertTrue(interceptor.beforeHandshake(request, response, handler, attributes));
        assertTrue(attributes.containsKey("userId"));
    }

    @Test
    void rejectsMissingOrIncompatibleProtocolBeforeTokenAuthentication() {
        headers.set("X-Agent-Device-Id", "device-1");
        headers.set("X-Agent-Protocol-Version", "2.0");
        headers.set("X-Agent-Bridge-Version", "0.1.0");
        headers.setBearerAuth("short-token");

        assertFalse(interceptor.beforeHandshake(request, response, handler, new HashMap<>()));
    }
}
