package io.metersphere.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.function.Supplier;

/** Fixed internal stage names only; no arguments, tokens or business facts are logged. */
final class AgentMcpTiming {
    private static final Logger LOG=LoggerFactory.getLogger(AgentMcpTiming.class);
    private AgentMcpTiming() { }

    static <T> T measure(String stage, Supplier<T> action) {
        long start=System.nanoTime();
        boolean success=false;
        try {
            T result=action.get(); success=true; return result;
        } finally {
            long elapsed=(System.nanoTime()-start)/1_000_000;
            if(elapsed>=1000) LOG.warn("MCP stage traceId={} stage={} success={} durationMs={}",MDC.get("traceId"),stage,success,elapsed);
            else LOG.debug("MCP stage traceId={} stage={} success={} durationMs={}",MDC.get("traceId"),stage,success,elapsed);
        }
    }
}
