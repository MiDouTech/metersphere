package io.metersphere.functional.socket;

import io.metersphere.functional.service.CaseExportNotificationTickets;
import io.metersphere.functional.service.FunctionalCaseExportTaskService;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Each replica reads shared task state; connections are local, task truth never is. */
@Component
public class CaseExportNotificationHandler extends TextWebSocketHandler implements SubProtocolCapable {
    @Resource private CaseExportNotificationTickets tickets;
    @Resource private FunctionalCaseExportTaskService tasks;
    @Value("${case.export.websocket.max-connections:100}") private int maxConnections = 100;
    @Value("${case.export.websocket.per-user-connections:8}") private int perUserConnections = 8;
    @Value("${case.export.websocket.poll-ms:2000}") private long pollMs = 2000;
    private final LongSupplier clock;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    // Keep slow notification sockets off the application's recovery/cleanup scheduler.
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "case-export-notification");
        thread.setDaemon(true);
        return thread;
    });
    public CaseExportNotificationHandler() { this(System::nanoTime); }
    CaseExportNotificationHandler(LongSupplier clock) { this.clock = clock; }
    private static final class Connection {
        final WebSocketSession session;
        final CaseExportNotificationTickets.Claims claims;
        final long created;
        long version = -1;
        String state;
        Connection(WebSocketSession session, CaseExportNotificationTickets.Claims claims, long created) {
            this.session = new ConcurrentWebSocketSessionDecorator(session, 5000, 65536);
            this.claims = claims;
            this.created = created;
        }
    }
    @PostConstruct public void validateConfiguration() {
        if (maxConnections < 1 || maxConnections > 1000 || perUserConnections < 1 || perUserConnections > maxConnections
                || pollMs < 1000 || pollMs > 30000) throw new IllegalArgumentException("Invalid export notification capacity");
        scheduler.scheduleWithFixedDelay(this::poll, pollMs, pollMs, TimeUnit.MILLISECONDS);
    }
    @Override public List<String> getSubProtocols() { return List.of(CaseExportNotificationTickets.PROTOCOL); }

    @Override public void afterConnectionEstablished(WebSocketSession session) {
        Object value = session.getAttributes().get(CaseExportTicketFilter.CLAIMS);
        if (!(value instanceof CaseExportNotificationTickets.Claims claims)) { close(session, CloseStatus.POLICY_VIOLATION); return; }
        session.setTextMessageSizeLimit(1024);
        session.setBinaryMessageSizeLimit(1);
        Connection connection = new Connection(session, claims, clock.getAsLong());
        synchronized (connections) {
            long owned = connections.values().stream().filter(item -> item.claims.userId().equals(claims.userId())).count();
            if (connections.size() >= maxConnections || owned >= perUserConnections) { close(session, CloseStatus.POLICY_VIOLATION); return; }
            connections.put(session.getId(), connection);
        }
        publish(connection);
    }

    public void poll() { for (Connection connection : connections.values()) publish(connection); }

    @jakarta.annotation.PreDestroy public void shutdown() {
        scheduler.shutdownNow();
        for (Connection connection : connections.values()) disconnect(connection, CloseStatus.GOING_AWAY);
    }

    private void publish(Connection connection) {
        synchronized (connection) {
            try {
                if (!connection.session.isOpen()) { connections.remove(connection.session.getId(), connection); return; }
                if (TimeUnit.NANOSECONDS.toMillis(clock.getAsLong() - connection.created) >= 600000) {
                    disconnect(connection, new CloseStatus(1012, "Reconnect with a fresh ticket")); return;
                }
                tickets.validateSession(connection.claims);
                var task = tasks.get(connection.claims.taskId(), connection.claims.userId());
                if (!Objects.equals(task.getProjectId(), connection.claims.projectId())) throw CaseExportNotificationTickets.denied();
                if (task.getStateVersion() < connection.version) return;
                if (task.getStateVersion() != connection.version || !Objects.equals(task.getState(), connection.state)) {
                    connection.session.sendMessage(new TextMessage(JSON.toJSONString(Map.of("type", "EXPORT_TASK", "task", task))));
                    connection.version = task.getStateVersion();
                    connection.state = task.getState();
                }
                if (!"RUNNING".equals(task.getState())) disconnect(connection, CloseStatus.NORMAL);
            } catch (CaseExportNotificationTickets.TicketException denied) {
                disconnect(connection, denied.status() == 503 ? CloseStatus.SERVER_ERROR : CloseStatus.POLICY_VIOLATION);
            } catch (io.metersphere.sdk.exception.MSException denied) {
                disconnect(connection, CloseStatus.POLICY_VIOLATION);
            } catch (Exception unavailable) {
                disconnect(connection, CloseStatus.SERVER_ERROR);
            }
        }
    }

    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Connection connection = connections.get(session.getId());
        if (connection == null) { close(session, CloseStatus.POLICY_VIOLATION); return; }
        synchronized (connection) {
            if (!"ping".equals(message.getPayload())) { disconnect(connection, CloseStatus.POLICY_VIOLATION); return; }
            try { connection.session.sendMessage(new TextMessage("pong")); }
            catch (IOException unavailable) { disconnect(connection, CloseStatus.SERVER_ERROR); }
        }
    }
    @Override protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        Connection connection = connections.get(session.getId());
        if (connection != null) disconnect(connection, CloseStatus.POLICY_VIOLATION);
        else close(session, CloseStatus.POLICY_VIOLATION);
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { connections.remove(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) {
        Connection connection = connections.get(session.getId());
        if (connection != null) disconnect(connection, CloseStatus.SERVER_ERROR);
        else close(session, CloseStatus.SERVER_ERROR);
    }
    private void disconnect(Connection connection, CloseStatus status) {
        connections.remove(connection.session.getId(), connection);
        close(connection.session, status);
    }
    private static void close(WebSocketSession session, CloseStatus status) {
        try { if (session.isOpen()) session.close(status); }
        catch (IOException | RuntimeException ignored) { /* Already disconnected; no credential-bearing session URI is logged. */ }
    }
}
