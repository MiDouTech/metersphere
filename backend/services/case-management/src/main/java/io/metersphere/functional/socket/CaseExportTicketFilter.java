package io.metersphere.functional.socket;

import io.metersphere.functional.service.CaseExportNotificationTickets;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/** This exact WS route has its own authentication, before Spring performs the upgrade. */
@Component
public class CaseExportTicketFilter extends OncePerRequestFilter {
    public static final String CLAIMS = CaseExportTicketFilter.class.getName() + ".claims";
    @Resource private CaseExportNotificationTickets tickets;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            if (!"GET".equals(request.getMethod()) || !"websocket".equalsIgnoreCase(request.getHeader("Upgrade"))
                    || request.getParameterMap().size() != 1 || request.getParameterValues("taskId") == null
                    || request.getParameterValues("taskId").length != 1) throw CaseExportNotificationTickets.denied();
            String header = request.getHeader("Sec-WebSocket-Protocol");
            if (header == null || header.length() > 150) throw CaseExportNotificationTickets.denied();
            String[] protocols = header.split(",", -1);
            if (protocols.length != 2 || !CaseExportNotificationTickets.PROTOCOL.equals(protocols[0].trim())
                    || !protocols[1].trim().startsWith("ticket.")) throw CaseExportNotificationTickets.denied();
            var claims = tickets.consume(protocols[1].trim().substring(7), request.getParameter("taskId"), request.getHeader("Origin"));
            request.setAttribute(CLAIMS, claims);
            // Only the public subprotocol reaches framework logging/negotiation; credentials are not in the URI.
            HttpServletRequest sanitized = new HttpServletRequestWrapper(request) {
                @Override public String getHeader(String name) {
                    return "Sec-WebSocket-Protocol".equalsIgnoreCase(name) ? CaseExportNotificationTickets.PROTOCOL : super.getHeader(name);
                }
                @Override public Enumeration<String> getHeaders(String name) {
                    return "Sec-WebSocket-Protocol".equalsIgnoreCase(name)
                            ? Collections.enumeration(List.of(CaseExportNotificationTickets.PROTOCOL)) : super.getHeaders(name);
                }
            };
            chain.doFilter(sanitized, response);
        } catch (CaseExportNotificationTickets.TicketException exception) {
            reject(response, exception.status(), exception.getMessage());
        } catch (RuntimeException failure) {
            reject(response, 503, "通知连接暂不可用，请刷新查看导出状态");
        }
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        if (response.isCommitted()) return;
        String requestId = UUID.randomUUID().toString();
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Request-Id", requestId);
        if (status == 429) response.setHeader("Retry-After", "60");
        response.getWriter().write(JSON.toJSONString(Map.of("code", 100000 + status, "message", message, "requestId", requestId)));
    }
}
