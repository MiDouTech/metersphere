package io.metersphere.system.service.ai.agent.bridge;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.ai.agent.AiAgentConnectionStatusRequest;
import io.metersphere.system.service.ai.agent.AiUserAgentService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;

public class AgentBridgeWebSocketHandler extends TextWebSocketHandler {
    private static final long MAX_CLOCK_SKEW_MS = 5 * 60_000L;
    private static final Set<String> UPSTREAM_TYPES = Set.of(
            "connection.ready", "connection.heartbeat", "connection.status", "execution.accepted", "message.start",
            "content.delta", "tool.call", "usage.reported", "execution.completed",
            "execution.failed", "execution.cancelled");
    private final AgentBridgeSessionRegistry registry;
    private final AiUserAgentService userAgentService;

    public AgentBridgeWebSocketHandler(AgentBridgeSessionRegistry registry, AiUserAgentService userAgentService) {
        this.registry = registry;
        this.userAgentService = userAgentService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.register(deviceId(session), session);
        userAgentService.heartbeatAuthenticated(deviceId(session));
        syncConnections(deviceId(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayloadLength() > 1024 * 1024) {
            session.close(CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }
        AgentBridgeEnvelope envelope;
        try {
            envelope = JSON.parseObject(message.getPayload(), AgentBridgeEnvelope.class);
        } catch (RuntimeException error) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        if (!StringUtils.equals(envelope.getProtocolVersion(), "1.0")
                || !UPSTREAM_TYPES.contains(envelope.getType())
                || Math.abs(System.currentTimeMillis() - envelope.getTimestamp()) > MAX_CLOCK_SKEW_MS) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        String deviceId = deviceId(session);
        if (StringUtils.equals(envelope.getType(), "connection.heartbeat")) {
            registry.heartbeat(deviceId);
            userAgentService.heartbeatAuthenticated(deviceId);
            syncConnections(deviceId);
            return;
        }
        if (StringUtils.equals(envelope.getType(), "connection.ready")) {
            syncConnections(deviceId);
            return;
        }
        if (StringUtils.equals(envelope.getType(), "connection.status")) {
            AiAgentConnectionStatusRequest request = JSON.parseObject(
                    JSON.toJSONString(envelope.getPayload()), AiAgentConnectionStatusRequest.class);
            request.setDeviceId(deviceId);
            userAgentService.reportConnectionStatusAuthenticated(request, deviceId);
            return;
        }
        if (StringUtils.isBlank(envelope.getRequestId()) || envelope.getSequence() <= 0) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        registry.accept(deviceId, envelope);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (registry.unregister(deviceId(session), session)) {
            userAgentService.markDeviceOfflineAuthenticated(deviceId(session));
        }
    }

    private String deviceId(WebSocketSession session) {
        return (String) session.getAttributes().get("deviceId");
    }

    private void syncConnections(String deviceId) {
        registry.syncConnections(deviceId, userAgentService.connectionRoutesForDevice(deviceId));
    }
}
