package io.metersphere.agent.service;

import io.metersphere.agent.security.*;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentMcpErrorRecoveryTests {
    @Test void rateLimitPreservesRpcEnvelopeTraceAndConfiguredWindow() {
        var limiter=mock(AgentTokenRateLimiter.class);
        when(limiter.tryAcquireTool("token","metersphere.project.search")).thenReturn(false);
        when(limiter.suggestedToolRetryAfterMs()).thenReturn(120000L);
        var service=new AgentMcpStreamableService();
        ReflectionTestUtils.setField(service,"agentTokenRateLimiter",limiter);
        ReflectionTestUtils.setField(service,"safeErrorMapper",new AgentSafeErrorMapper());
        var token=new AgentToken(); token.setId("token");
        AgentTokenContext.set(token);
        org.slf4j.MDC.put("traceId","request-trace");
        try {
            var result=service.handle(Map.of("jsonrpc","2.0","id",17,"method","tools/call",
                    "params",Map.of("name","metersphere.project.search","arguments",Map.of())));
            assertEquals(17,result.get("id"));
            var error=(Map<?,?>)result.get("error");
            var data=(Map<?,?>)error.get("data");
            assertEquals("AGENT_RATE_LIMITED",data.get("code"));
            assertEquals("request-trace",data.get("traceId"));
            var details=(Map<?,?>)data.get("details");
            assertEquals(120000L,details.get("retryAfterMs"));
            assertEquals("CONFIGURED_WINDOW_UPPER_BOUND",details.get("retryAfterBasis"));
            assertEquals(true,details.get("retryable"));
        } finally {
            AgentTokenContext.clear(); org.slf4j.MDC.remove("traceId");
        }
    }
}
