package io.metersphere.system.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/** Correlation IDs contain no identity or request payload; clear pooled-thread context after every request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestTraceFilter extends OncePerRequestFilter {
    private static final Logger LOG=LoggerFactory.getLogger(RequestTraceFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        com.github.pagehelper.PageHelper.clearPage();
        String candidate=request.getHeader("X-Request-ID");
        String id=candidate!=null && candidate.matches("[A-Za-z0-9_.:-]{1,128}")?candidate:UUID.randomUUID().toString();
        String previous=MDC.get("traceId");
        request.setAttribute("agentTraceId",id);request.setAttribute("requestId",id);
        response.setHeader("X-Request-ID",id);MDC.put("traceId",id);
        long start=System.nanoTime();
        var previousDb = io.metersphere.system.interceptor.DatabaseQueryTelemetry.startRequest();
        try {chain.doFilter(request,response);}
        finally {
            // Empty/short-circuited page handlers may never reach MyBatis's cleanup.
            com.github.pagehelper.PageHelper.clearPage();
            long ms=(System.nanoTime()-start)/1_000_000;
            var db = io.metersphere.system.interceptor.DatabaseQueryTelemetry.finishRequest(previousDb);
            Object route=request.getAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern");
            if(ms>=3000 || response.getStatus()>=500)LOG.warn("HTTP_REQUEST method={} route={} status={} durationMs={} dbOperations={} dbOperationMs={}",request.getMethod(),route==null?"UNMATCHED":route,response.getStatus(),ms,db==null?0:db.operations,db==null?0:db.elapsedNanos/1_000_000);
            if(previous==null)MDC.remove("traceId");else MDC.put("traceId",previous);
        }
    }
}
