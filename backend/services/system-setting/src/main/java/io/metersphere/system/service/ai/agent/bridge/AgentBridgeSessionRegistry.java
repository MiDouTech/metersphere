package io.metersphere.system.service.ai.agent.bridge;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.service.ai.agent.AgentStreamEvent;
import io.metersphere.system.service.ai.agent.UserAgentExecutionRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class AgentBridgeSessionRegistry implements MessageListener {
    private static final String ROUTE_PREFIX = "ms:ai:agent-bridge:route:";
    private static final String NODE_CHANNEL_PREFIX = "ms:ai:agent-bridge:node:";
    private final Map<String, WebSocketSession> devices = new ConcurrentHashMap<>();
    private final Map<String, PendingExecution> executions = new ConcurrentHashMap<>();
    private final Map<String, RemoteExecution> remoteExecutions = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    @Value("${ms.ai.user-agent.gateway-node-id:${HOSTNAME:local}}")
    private String nodeId;
    @Value("${ms.ai.user-agent.receive-timeout-ms:15000}")
    private long receiveTimeoutMs;
    @Value("${ms.ai.user-agent.first-content-timeout-ms:120000}")
    private long firstContentTimeoutMs;
    @Value("${ms.ai.user-agent.idle-timeout-ms:60000}")
    private long idleTimeoutMs;
    @Value("${ms.ai.user-agent.total-timeout-ms:14400000}")
    private long totalTimeoutMs;

    public AgentBridgeSessionRegistry(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void register(String deviceId, WebSocketSession session) {
        WebSocketSession previous = devices.put(deviceId, session);
        if (previous != null && previous.isOpen()) {
            try {
                previous.close();
            } catch (IOException ignored) {
                // The newly authenticated connection remains authoritative.
            }
        }
        refreshRoute(deviceId);
    }

    public boolean unregister(String deviceId, WebSocketSession session) {
        if (!devices.remove(deviceId, session)) {
            return false;
        }
        try {
            String route = redisTemplate.opsForValue().get(ROUTE_PREFIX + deviceId);
            if (StringUtils.equals(route, nodeId)) {
                redisTemplate.delete(ROUTE_PREFIX + deviceId);
            }
        } catch (Exception error) {
            log.warn("Agent Bridge route cleanup failed deviceId={}", deviceId);
        }
        return true;
    }

    public void heartbeat(String deviceId) {
        refreshRoute(deviceId);
    }

    public void syncConnections(String deviceId, java.util.List<Map<String, Object>> connections) {
        WebSocketSession session = devices.get(deviceId);
        if (session != null && session.isOpen()) {
            send(session, envelope("connection.sync", "connection", Map.of("connections", connections)));
        }
    }

    public void authorize(String deviceId, String connectionId, String provider) {
        sendToDevice(deviceId, envelope("connection.authorize", "connection:" + connectionId,
                Map.of("connectionId", connectionId, "provider", provider)));
    }

    public Flux<AgentStreamEvent> start(UserAgentExecutionRequest request) {
        PendingExecution pending = new PendingExecution(request.deviceId());
        if (executions.putIfAbsent(request.requestId(), pending) != null) {
            throw new MSException("Agent 执行 requestId 已存在");
        }
        try {
            sendToDevice(request.deviceId(), envelope("execution.start", request.requestId(), Map.of(
                    "conversationId", request.conversationId(),
                    "projectId", request.projectId(),
                    "connectionId", request.connectionId(),
                    "provider", request.provider(),
                    "systemPrompt", request.systemPrompt(),
                    "prompt", request.prompt(),
                    "externalSessionId", StringUtils.defaultString(request.externalSessionId()),
                    "allowedTools", request.allowedTools(),
                    "limits", request.limits())));
        } catch (RuntimeException error) {
            executions.remove(request.requestId());
            throw error;
        }
        Flux<AgentStreamEvent> source = pending.sink.asFlux()
                .mergeWith(pending.abort.asMono().flatMapMany(Flux::error))
                .doFinally(signal -> executions.remove(request.requestId(), pending));
        AtomicBoolean received = new AtomicBoolean();
        return source.publish(shared -> Flux.merge(
                        shared.doOnNext(ignored -> received.set(true))
                                .timeout(Mono.delay(Duration.ofMillis(Math.max(1, receiveTimeoutMs))),
                                        ignored -> Mono.delay(Duration.ofMillis(Math.max(1, idleTimeoutMs))))
                                .onErrorMap(TimeoutException.class, error -> new MSException(received.get()
                                        ? "AGENT_IDLE_TIMEOUT" : "AGENT_RECEIVE_TIMEOUT")),
                        Mono.delay(Duration.ofMillis(Math.max(1, firstContentTimeoutMs)))
                                .takeUntilOther(shared.filter(this::isContentOrToolEvent).next())
                                .flatMap(ignored -> Mono.<AgentStreamEvent>error(
                                        new MSException("AGENT_FIRST_CONTENT_TIMEOUT")))
                                .flux()))
                .timeout(Duration.ofMillis(executionTotalTimeoutMs(request)))
                .onErrorMap(TimeoutException.class, error -> new MSException("AGENT_TOTAL_TIMEOUT"));
    }

    public void accept(String deviceId, AgentBridgeEnvelope envelope) {
        PendingExecution pending = executions.get(envelope.getRequestId());
        if (pending != null && StringUtils.equals(pending.deviceId, deviceId)) {
            acceptPending(pending, envelope);
            return;
        }
        RemoteExecution remote = remoteExecutions.get(envelope.getRequestId());
        if (remote == null || !StringUtils.equals(remote.deviceId, deviceId)) {
            return;
        }
        publish(remote.originNode, AgentBridgeBusMessage.upstream(
                nodeId, remote.originNode, deviceId, envelope));
        if (isTerminal(envelope.getType())) {
            remoteExecutions.remove(envelope.getRequestId(), remote);
        }
    }

    private void acceptPending(PendingExecution pending, AgentBridgeEnvelope envelope) {
        long sequence = envelope.getSequence();
        long current;
        do {
            current = pending.lastSequence.get();
            if (sequence <= current) {
                return;
            }
            if (sequence != current + 1) {
                pending.abort.tryEmitValue(new MSException("AGENT_PROTOCOL_SEQUENCE_GAP"));
                return;
            }
        } while (!pending.lastSequence.compareAndSet(current, sequence));
        AgentStreamEvent event = new AgentStreamEvent(envelope.getType(), envelope.getRequestId(), sequence,
                envelope.getPayload() == null ? Map.of() : envelope.getPayload());
        Sinks.EmitResult emitted = pending.sink.tryEmitNext(event);
        if (!emitted.isSuccess()) {
            pending.abort.tryEmitValue(new MSException("AGENT_BACKPRESSURE_LIMIT_EXCEEDED"));
            cancel(envelope.getRequestId());
            return;
        }
        if (StringUtils.equalsAny(envelope.getType(), "execution.completed", "execution.cancelled")) {
            pending.sink.tryEmitComplete();
            pending.abort.tryEmitEmpty();
        } else if (StringUtils.equals(envelope.getType(), "execution.failed")) {
            pending.abort.tryEmitValue(new MSException(StringUtils.defaultString(
                    (String) event.payload().get("message"), "Agent 执行失败")));
        }
    }

    public void toolResult(String requestId, String toolCallId, boolean success,
                           Map<String, Object> result, String errorCode) {
        PendingExecution pending = executions.get(requestId);
        if (pending == null) {
            throw new MSException("Agent 执行不存在或已结束");
        }
        sendToDevice(pending.deviceId, envelope("tool.result", requestId, Map.of(
                "toolCallId", toolCallId, "success", success, "result", result,
                "errorCode", StringUtils.defaultString(errorCode))));
    }

    public void cancel(String requestId) {
        PendingExecution pending = executions.get(requestId);
        if (pending != null) {
            sendToDevice(pending.deviceId, envelope("execution.cancel", requestId, Map.of()));
        }
    }

    public String nodeChannel() {
        return NODE_CHANNEL_PREFIX + nodeId;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            AgentBridgeBusMessage bus = JSON.parseObject(
                    new String(message.getBody(), StandardCharsets.UTF_8), AgentBridgeBusMessage.class);
            if (StringUtils.equals(bus.getKind(), "DOWNSTREAM")) {
                acceptDownstream(bus);
            } else if (StringUtils.equals(bus.getKind(), "UPSTREAM")
                    && StringUtils.equals(bus.getTargetNode(), nodeId)) {
                PendingExecution pending = executions.get(bus.getEnvelope().getRequestId());
                if (pending != null && StringUtils.equals(pending.deviceId, bus.getDeviceId())) {
                    acceptPending(pending, bus.getEnvelope());
                }
            }
        } catch (Exception error) {
            log.warn("Agent Bridge node bus message rejected", error);
        }
    }

    private void acceptDownstream(AgentBridgeBusMessage bus) {
        AgentBridgeEnvelope downstream = bus.getEnvelope();
        if (downstream == null || StringUtils.isBlank(bus.getOriginNode())
                || StringUtils.isBlank(bus.getDeviceId())) {
            return;
        }
        if (StringUtils.equals(downstream.getType(), "execution.start")) {
            remoteExecutions.put(downstream.getRequestId(),
                    new RemoteExecution(bus.getOriginNode(), bus.getDeviceId()));
        }
        WebSocketSession session = devices.get(bus.getDeviceId());
        if (session == null || !session.isOpen()) {
            remoteExecutions.remove(downstream.getRequestId());
            if (StringUtils.equals(downstream.getType(), "execution.start")) {
                AgentBridgeEnvelope failed = envelope("execution.failed", downstream.getRequestId(),
                        Map.of("message", "AGENT_OFFLINE：Bridge 路由已过期"));
                failed.setSequence(1);
                publish(bus.getOriginNode(), AgentBridgeBusMessage.upstream(
                        nodeId, bus.getOriginNode(), bus.getDeviceId(), failed));
            }
            return;
        }
        send(session, downstream);
    }

    private void sendToDevice(String deviceId, AgentBridgeEnvelope envelope) {
        WebSocketSession local = devices.get(deviceId);
        if (local != null && local.isOpen()) {
            send(local, envelope);
            return;
        }
        String route = route(deviceId);
        if (StringUtils.isBlank(route) || StringUtils.equals(route, nodeId)) {
            throw new MSException("AGENT_OFFLINE：Bridge 未连接");
        }
        publish(route, AgentBridgeBusMessage.downstream(nodeId, route, deviceId, envelope));
    }

    private String route(String deviceId) {
        try {
            return redisTemplate.opsForValue().get(ROUTE_PREFIX + deviceId);
        } catch (Exception error) {
            throw new MSException("AGENT_GATEWAY_ROUTE_UNAVAILABLE");
        }
    }

    private void publish(String targetNode, AgentBridgeBusMessage message) {
        try {
            Long receivers = redisTemplate.convertAndSend(NODE_CHANNEL_PREFIX + targetNode,
                    JSON.toJSONString(message));
            if (receivers == null || receivers == 0) {
                throw new MSException("AGENT_GATEWAY_REMOTE_ROUTE_UNAVAILABLE");
            }
        } catch (MSException error) {
            throw error;
        } catch (Exception error) {
            throw new MSException("AGENT_GATEWAY_REMOTE_ROUTE_UNAVAILABLE");
        }
    }

    private void refreshRoute(String deviceId) {
        try {
            redisTemplate.opsForValue().set(ROUTE_PREFIX + deviceId, nodeId, Duration.ofSeconds(45));
        } catch (Exception error) {
            log.warn("Agent Bridge route refresh failed deviceId={}", deviceId);
        }
    }

    private AgentBridgeEnvelope envelope(String type, String requestId, Map<String, Object> payload) {
        AgentBridgeEnvelope envelope = new AgentBridgeEnvelope();
        envelope.setProtocolVersion("1.0");
        envelope.setType(type);
        envelope.setRequestId(requestId);
        envelope.setSequence(0);
        envelope.setTimestamp(System.currentTimeMillis());
        envelope.setPayload(payload);
        return envelope;
    }

    private void send(WebSocketSession session, AgentBridgeEnvelope envelope) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(JSON.toJSONString(envelope)));
            }
        } catch (IOException error) {
            throw new MSException("Agent Bridge 消息发送失败");
        }
    }

    private boolean isTerminal(String type) {
        return StringUtils.equalsAny(type, "execution.completed", "execution.failed", "execution.cancelled");
    }

    private boolean isContentOrToolEvent(AgentStreamEvent event) {
        return StringUtils.equalsAny(event.type(), "content.delta", "tool.call");
    }

    private long executionTotalTimeoutMs(UserAgentExecutionRequest request) {
        Object seconds = request.limits() == null ? null : request.limits().get("maxExecutionSeconds");
        long requestedMs = seconds instanceof Number value ? Math.max(1, value.longValue()) * 1000L : totalTimeoutMs;
        return Math.max(1, Math.min(Math.max(1, totalTimeoutMs), requestedMs));
    }

    private static final class PendingExecution {
        private final String deviceId;
        private final AtomicLong lastSequence = new AtomicLong();
        private final Sinks.Many<AgentStreamEvent> sink = Sinks.many().unicast()
                .onBackpressureBuffer(new ArrayBlockingQueue<>(512));
        private final Sinks.One<Throwable> abort = Sinks.one();

        private PendingExecution(String deviceId) {
            this.deviceId = deviceId;
        }
    }

    private record RemoteExecution(String originNode, String deviceId) {
    }
}
