package io.metersphere.system.service.ai.agent.bridge;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.service.ai.agent.AiUserAgentService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class AgentBridgeHandshakeInterceptor implements HandshakeInterceptor {
    private static final String SUPPORTED_PROTOCOL_VERSION = "1.0";
    private final AiUserAgentService service;

    public AgentBridgeHandshakeInterceptor(AiUserAgentService service) {
        this.service = service;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String deviceId = request.getHeaders().getFirst("X-Agent-Device-Id");
        String protocolVersion = request.getHeaders().getFirst("X-Agent-Protocol-Version");
        String bridgeVersion = request.getHeaders().getFirst("X-Agent-Bridge-Version");
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.isBlank(deviceId)
                || !StringUtils.equals(protocolVersion, SUPPORTED_PROTOCOL_VERSION)
                || !isCompatibleBridgeVersion(bridgeVersion)
                || !StringUtils.startsWithIgnoreCase(authorization, "Bearer ")) {
            return false;
        }
        try {
            Map<String, Object> device = service.authenticateToken(deviceId,
                    StringUtils.trim(StringUtils.substring(authorization, 7)));
            if (!StringUtils.equals(protocolVersion, (String) device.get("protocol_version"))) {
                return false;
            }
            attributes.put("deviceId", deviceId);
            attributes.put("userId", device.get("user_id"));
            return true;
        } catch (MSException error) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No secret material is retained after authentication.
    }

    private boolean isCompatibleBridgeVersion(String value) {
        if (StringUtils.isBlank(value) || value.length() > 64
                || !value.matches("\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?")) {
            return false;
        }
        try {
            String[] parts = value.split("[-+]", 2)[0].split("\\.");
            return Integer.parseInt(parts[0]) > 0 || Integer.parseInt(parts[1]) > 0;
        } catch (NumberFormatException error) {
            return false;
        }
    }
}
