package io.metersphere.agent.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/** Server generated correlation only; no caller-controlled headers or request bodies enter logs. */
@Component
public class AgentProtocolTraceFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI();
        return !(path.contains("/governed-execution")||path.contains("/execution-sessions")||path.contains("/test-knowledge"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String previous=MDC.get("traceId"),trace=UUID.randomUUID().toString();MDC.put("traceId",trace);
        request.setAttribute("agentTraceId",trace);
        response.setHeader("X-Protocol-Trace-Id",trace);response.setHeader("Cache-Control","no-store");
        long started=System.nanoTime();
        try{chain.doFilter(request,response);}
        finally{
            org.slf4j.LoggerFactory.getLogger(getClass()).info("Protocol HTTP traceId={} stage=HTTP_COMPLETED status={} elapsedMs={}",trace,response.getStatus(),(System.nanoTime()-started)/1_000_000);
            if(previous==null)MDC.remove("traceId");else MDC.put("traceId",previous);
        }
    }
}
