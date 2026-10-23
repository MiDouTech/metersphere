package io.metersphere.functional.socket;

import io.metersphere.functional.service.CaseExportNotificationTickets;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CaseExportTicketFilterTests {
    @Test void dedicatedRoutesUseTicketAuthenticationWithoutAnonymousOrGlobalFilterRegistration() {
        var config = new CaseExportNotificationConfig(); var filter = new CaseExportTicketFilter();
        ReflectionTestUtils.setField(config, "ticketFilter", filter);
        var filters = new java.util.LinkedHashMap<String, jakarta.servlet.Filter>();
        var chains = new java.util.LinkedHashMap<String, String>();
        config.extend(filters, chains);
        assertSame(filter, filters.get("caseExportTicket"));
        assertEquals(java.util.Map.of("/functional/case/export/ws", "caseExportTicket",
                "/api/functional/case/export/ws", "caseExportTicket"), chains);
        assertFalse(config.exportTicketServletRegistration(filter).isEnabled());
    }
    @Test void authenticatedUpgradeStripsCredentialBeforeFrameworkHandling() throws Exception {
        var tickets = mock(CaseExportNotificationTickets.class); var filter = new CaseExportTicketFilter();
        ReflectionTestUtils.setField(filter, "tickets", tickets);
        var claims = new CaseExportNotificationTickets.Claims("u","p","t","s","http://localhost", CaseExportNotificationTickets.PURPOSE);
        when(tickets.consume("secret", "t", "http://localhost")).thenReturn(claims);
        var request = request(); var response = new MockHttpServletResponse(); var called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> {
            called.set(true); var http = (HttpServletRequest) req;
            assertEquals(CaseExportNotificationTickets.PROTOCOL, http.getHeader("Sec-WebSocket-Protocol"));
            assertEquals(CaseExportNotificationTickets.PROTOCOL, http.getHeaders("Sec-WebSocket-Protocol").nextElement());
            assertSame(claims, http.getAttribute(CaseExportTicketFilter.CLAIMS));
        });
        assertTrue(called.get());
    }
    @Test void missingCredentialsExtraQueryAndReplayNeverReachUpgrade() throws Exception {
        var tickets = mock(CaseExportNotificationTickets.class); var filter = new CaseExportTicketFilter();
        ReflectionTestUtils.setField(filter, "tickets", tickets);
        when(tickets.consume(anyString(), anyString(), anyString())).thenThrow(CaseExportNotificationTickets.denied());
        for (int variant = 0; variant < 3; variant++) {
            var request = request(); if (variant == 0) request.removeHeader("Sec-WebSocket-Protocol");
            if (variant == 1) request.addParameter("token", "forbidden");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> fail("Unauthorized upgrade"));
            assertEquals(403, response.getStatus()); assertEquals("no-store", response.getHeader("Cache-Control"));
            assertFalse(response.getContentAsString().contains("secret"));
        }
    }
    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("GET", "/functional/case/export/ws");
        request.addHeader("Upgrade", "websocket"); request.addHeader("Origin", "http://localhost");
        request.addHeader("Sec-WebSocket-Protocol", "ms-case-export-v1, ticket.secret"); request.addParameter("taskId", "t");
        return request;
    }
}
