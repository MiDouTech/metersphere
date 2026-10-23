package io.metersphere.system.security;

import io.metersphere.sdk.util.JSON;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Semaphore;

/** Admit bounded expensive traffic before authentication can acquire database connections. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class DatabaseWorkloadFilter extends OncePerRequestFilter {
    private final Semaphore permits;
    private final int concurrency;
    private final java.util.concurrent.atomic.LongAdder rejected = new java.util.concurrent.atomic.LongAdder();

    public int activeRequests() { return concurrency - permits.availablePermits(); }
    public long rejectedRequests() { return rejected.sum(); }

    public DatabaseWorkloadFilter(@Value("${ms.db.heavy-request-concurrency:8}") int concurrency) {
        if (concurrency < 1) throw new IllegalArgumentException("Heavy request concurrency must be positive");
        permits = new Semaphore(concurrency);
        this.concurrency = concurrency;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/")) path = path.substring(4);
        return !(path.equals("/mcp") || path.startsWith("/agent/v1/functional/") && !path.endsWith("/health")
                || path.startsWith("/functional/case/import/") || path.equals("/ai/case-imports/precheck")
                || path.equals("/functional/case/page")
                || path.equals("/functional/case/asset/page"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!permits.tryAcquire()) {
            rejected.increment();
            response.setStatus(503);
            response.setHeader("Retry-After", "3");
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JSON.toJSONString(Map.of("code", 503,
                    "message", "当前任务较多，请稍后重试，勿重复提交", "traceId",
                    String.valueOf(request.getAttribute("requestId")))));
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // Async servlet work retains its slot until completion, timeout or error.
            if (request.isAsyncStarted()) {
                java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
                Runnable release = () -> { if (released.compareAndSet(false, true)) permits.release(); };
                jakarta.servlet.AsyncListener listener = new jakarta.servlet.AsyncListener() {
                    public void onComplete(jakarta.servlet.AsyncEvent event) { release.run(); }
                    public void onTimeout(jakarta.servlet.AsyncEvent event) { release.run(); }
                    public void onError(jakarta.servlet.AsyncEvent event) { release.run(); }
                    public void onStartAsync(jakarta.servlet.AsyncEvent event) { event.getAsyncContext().addListener(this); }
                };
                try { request.getAsyncContext().addListener(listener); }
                catch (IllegalStateException completed) { release.run(); }
            } else {
                permits.release();
            }
        }
    }
}
