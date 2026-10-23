package io.metersphere.functional.socket;

import io.metersphere.functional.service.CaseExportNotificationTickets;
import io.metersphere.sdk.util.ShiroFilterChainExtender;
import jakarta.annotation.Resource;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocket
public class CaseExportNotificationConfig implements WebSocketConfigurer, ShiroFilterChainExtender {
    @Resource private CaseExportTicketFilter ticketFilter;
    @Resource private CaseExportNotificationHandler handler;
    @Resource private CaseExportNotificationTickets tickets;

    @Bean public FilterRegistrationBean<CaseExportTicketFilter> exportTicketServletRegistration(CaseExportTicketFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Shiro owns the exact-route filter, never register it on /*.
        return registration;
    }
    @Override public void extend(Map<String, Filter> filters, Map<String, String> chain) {
        filters.put("caseExportTicket", ticketFilter);
        chain.put("/functional/case/export/ws", "caseExportTicket");
        chain.put("/api/functional/case/export/ws", "caseExportTicket");
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/functional/case/export/ws", "/api/functional/case/export/ws")
                .setAllowedOrigins(tickets.allowedOrigins().toArray(String[]::new))
                .addInterceptors(new HandshakeInterceptor() {
                    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                             WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        if (request instanceof ServletServerHttpRequest servlet
                                && servlet.getServletRequest().getAttribute(CaseExportTicketFilter.CLAIMS) instanceof CaseExportNotificationTickets.Claims claims) {
                            attributes.put(CaseExportTicketFilter.CLAIMS, claims);
                            return true;
                        }
                        response.setStatusCode(HttpStatus.FORBIDDEN);
                        return false;
                    }
                    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                         WebSocketHandler wsHandler, Exception exception) {
                        // The single-use credential is consumed before upgrade; no credential survives to clean up here.
                    }
                });
    }
}
