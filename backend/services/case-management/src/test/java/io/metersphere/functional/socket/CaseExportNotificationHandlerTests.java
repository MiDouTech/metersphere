package io.metersphere.functional.socket;

import io.metersphere.functional.dto.ExportTaskDTO;
import io.metersphere.functional.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CaseExportNotificationHandlerTests {
    private final CaseExportNotificationTickets tickets = mock(CaseExportNotificationTickets.class);
    private final FunctionalCaseExportTaskService tasks = mock(FunctionalCaseExportTaskService.class);
    private final AtomicLong clock = new AtomicLong();
    private final CaseExportNotificationTickets.Claims claims = new CaseExportNotificationTickets.Claims("u", "p", "t", "s", "http://localhost", CaseExportNotificationTickets.PURPOSE);
    private CaseExportNotificationHandler handler() {
        var handler = new CaseExportNotificationHandler(clock::get);
        ReflectionTestUtils.setField(handler, "tickets", tickets);
        ReflectionTestUtils.setField(handler, "tasks", tasks);
        return handler;
    }
    private WebSocketSession session(String id) {
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id); when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of(CaseExportTicketFilter.CLAIMS, claims));
        return session;
    }
    private void state(long version, String state) {
        var task = new ExportTaskDTO(); task.setTaskId("t"); task.setProjectId("p"); task.setState(state); task.setStateVersion(version);
        when(tasks.get("t", "u")).thenReturn(task);
    }
    @Test void multipleConnectionsAndReplicasObserveSharedVersionWithoutRegression() throws Exception {
        var first = handler(); var second = handler();
        var a = session("a"); var b = session("b"); var c = session("c");
        state(2, "RUNNING"); first.afterConnectionEstablished(a); first.afterConnectionEstablished(b); second.afterConnectionEstablished(c);
        state(1, "RUNNING"); first.poll(); second.poll();
        for (var session : new WebSocketSession[]{a,b,c}) verify(session, times(1)).sendMessage(any());
        state(3, "SUCCEEDED"); first.poll(); second.poll();
        for (var session : new WebSocketSession[]{a,b,c}) {
            verify(session, times(2)).sendMessage(any()); verify(session).close(CloseStatus.NORMAL);
        }
        first.poll(); second.poll();
        verify(a, times(2)).sendMessage(any());
    }
    @Test void revokedSessionClosesAndClientCannotForgeTaskBroadcast() throws Exception {
        var handler = handler(); var a = session("a"); var b = session("b");
        state(1, "RUNNING"); handler.afterConnectionEstablished(a); handler.afterConnectionEstablished(b);
        handler.handleTextMessage(a, new TextMessage("{\"state\":\"SUCCEEDED\"}"));
        verify(a).close(CloseStatus.POLICY_VIOLATION); verify(b, times(1)).sendMessage(any());
        doThrow(CaseExportNotificationTickets.denied()).when(tickets).validateSession(claims);
        handler.poll(); verify(b).close(CloseStatus.POLICY_VIOLATION);
    }
    @Test void capacityAndLifetimeAreBoundedAndClosedConnectionsReleaseCapacity() throws Exception {
        var handler = handler(); ReflectionTestUtils.setField(handler, "perUserConnections", 1);
        state(1, "RUNNING"); var a = session("a"); var b = session("b");
        handler.afterConnectionEstablished(a); handler.afterConnectionEstablished(b);
        verify(b).close(CloseStatus.POLICY_VIOLATION); verify(b, never()).sendMessage(any());
        handler.afterConnectionClosed(a, CloseStatus.NORMAL);
        var c = session("c"); handler.afterConnectionEstablished(c); verify(c).sendMessage(any());
        clock.set(java.util.concurrent.TimeUnit.MINUTES.toNanos(10)); handler.poll();
        var status = org.mockito.ArgumentCaptor.forClass(CloseStatus.class); verify(c).close(status.capture());
        assertEquals(1012, status.getValue().getCode());
    }
}
