package io.metersphere.system.service.ai.agent.bridge;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.service.ai.agent.AgentStreamEvent;
import io.metersphere.system.service.ai.agent.UserAgentExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.connection.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class AgentBridgeSessionRegistryTests {
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private WebSocketSession session;
    private AgentBridgeSessionRegistry registry;

    @BeforeEach
    void setup() {
        registry = new AgentBridgeSessionRegistry(redisTemplate);
        configureTimeouts(registry, 1_000, 1_000, 1_000, 1_000);
        lenient().when(session.isOpen()).thenReturn(true);
        registry.register("device-1", session);
    }

    @Test
    void duplicateSequenceIsIgnoredWithoutDuplicatingContent() {
        Flux<AgentStreamEvent> events = registry.start(request("request-1"));
        registry.accept("device-1", event("request-1", 1, "content.delta"));
        registry.accept("device-1", event("request-1", 1, "content.delta"));
        registry.accept("device-1", event("request-1", 2, "execution.completed"));

        List<AgentStreamEvent> received = events.collectList().block(Duration.ofSeconds(1));
        assertEquals(2, received.size());
        assertEquals(List.of(1L, 2L), received.stream().map(AgentStreamEvent::sequence).toList());
    }

    @Test
    void sequenceGapTerminatesExecutionWithExplicitProtocolError() {
        Flux<AgentStreamEvent> events = registry.start(request("request-2"));
        registry.accept("device-1", event("request-2", 2, "content.delta"));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> events.blockLast(Duration.ofSeconds(1)));
        Throwable cause = error instanceof MSException ? error : error.getCause();
        assertEquals("AGENT_PROTOCOL_SEQUENCE_GAP", cause.getMessage());
    }

    @Test
    void boundedQueueTerminatesProducerWhenConsumerDoesNotSubscribe() {
        Flux<AgentStreamEvent> events = registry.start(request("request-3"));
        for (int sequence = 1; sequence <= 513; sequence++) {
            registry.accept("device-1", event("request-3", sequence, "content.delta"));
        }

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> events.blockLast(Duration.ofSeconds(1)));
        Throwable cause = error instanceof MSException ? error : error.getCause();
        assertEquals("AGENT_BACKPRESSURE_LIMIT_EXCEEDED", cause.getMessage());
    }

    @Test
    void missingBridgeReceiptUsesExplicitReceiveTimeout() {
        configureTimeouts(registry, 20, 1_000, 1_000, 1_000);
        Flux<AgentStreamEvent> events = registry.start(request("request-timeout"));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> events.blockLast(Duration.ofSeconds(1)));
        Throwable cause = error instanceof MSException ? error : error.getCause();
        assertEquals("AGENT_RECEIVE_TIMEOUT", cause.getMessage());
    }

    @Test
    void routesExecutionToDeviceNodeAndReturnsEventsToOriginNode() {
        StringRedisTemplate originRedis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> originValues = mock(ValueOperations.class);
        when(originRedis.opsForValue()).thenReturn(originValues);
        when(originValues.get("ms:ai:agent-bridge:route:device-2")).thenReturn("node-b");
        when(originRedis.convertAndSend(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(1L);
        AgentBridgeSessionRegistry origin = new AgentBridgeSessionRegistry(originRedis);
        ReflectionTestUtils.setField(origin, "nodeId", "node-a");
        configureTimeouts(origin, 1_000, 1_000, 1_000, 1_000);

        StringRedisTemplate targetRedis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> targetValues = mock(ValueOperations.class);
        when(targetRedis.opsForValue()).thenReturn(targetValues);
        when(targetRedis.convertAndSend(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(1L);
        AgentBridgeSessionRegistry target = new AgentBridgeSessionRegistry(targetRedis);
        ReflectionTestUtils.setField(target, "nodeId", "node-b");
        WebSocketSession targetSession = mock(WebSocketSession.class);
        when(targetSession.isOpen()).thenReturn(true);
        target.register("device-2", targetSession);

        UserAgentExecutionRequest request = new UserAgentExecutionRequest("request-remote", "conversation",
                "project", "connection", "device-2", "CODEX", "system", "prompt", null,
                List.of(), Map.of());
        Flux<AgentStreamEvent> events = origin.start(request);
        ArgumentCaptor<String> downstream = ArgumentCaptor.forClass(String.class);
        verify(originRedis).convertAndSend(org.mockito.ArgumentMatchers.eq(
                "ms:ai:agent-bridge:node:node-b"), downstream.capture());
        target.onMessage(redisMessage(downstream.getValue()), null);

        target.accept("device-2", event("request-remote", 1, "execution.completed"));
        ArgumentCaptor<String> upstream = ArgumentCaptor.forClass(String.class);
        verify(targetRedis).convertAndSend(org.mockito.ArgumentMatchers.eq(
                "ms:ai:agent-bridge:node:node-a"), upstream.capture());
        origin.onMessage(redisMessage(upstream.getValue()), null);

        List<AgentStreamEvent> received = events.collectList().block(Duration.ofSeconds(1));
        assertEquals(1, received.size());
        assertEquals("execution.completed", received.getFirst().type());
    }

    private UserAgentExecutionRequest request(String requestId) {
        return new UserAgentExecutionRequest(requestId, "conversation", "project", "connection",
                "device-1", "CODEX", "system", "prompt", null, List.of(), Map.of());
    }

    private AgentBridgeEnvelope event(String requestId, long sequence, String type) {
        AgentBridgeEnvelope event = new AgentBridgeEnvelope();
        event.setProtocolVersion("1.0");
        event.setRequestId(requestId);
        event.setSequence(sequence);
        event.setType(type);
        event.setPayload(Map.of("delta", "value"));
        return event;
    }

    private Message redisMessage(String body) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private void configureTimeouts(AgentBridgeSessionRegistry target, long receive, long firstContent,
                                   long idle, long total) {
        ReflectionTestUtils.setField(target, "receiveTimeoutMs", receive);
        ReflectionTestUtils.setField(target, "firstContentTimeoutMs", firstContent);
        ReflectionTestUtils.setField(target, "idleTimeoutMs", idle);
        ReflectionTestUtils.setField(target, "totalTimeoutMs", total);
    }
}
