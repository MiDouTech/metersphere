package io.metersphere.system.security;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class RequestTraceFilterTests {
    @Test void propagatesSafeIdentifierAndClearsThreadContext()throws Exception {
        var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
        request.addHeader("X-Request-ID","request-123");
        new RequestTraceFilter().doFilter(request,response,(req,res)->{
            assertEquals("request-123",MDC.get("traceId"));
            assertEquals("request-123",req.getAttribute("agentTraceId"));
        });
        assertEquals("request-123",response.getHeader("X-Request-ID"));
        assertNull(MDC.get("traceId"));
    }
    @Test void rejectsUnboundedIdentifierAndRestoresExistingContextOnFailure() {
        var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
        request.addHeader("X-Request-ID","x".repeat(1000));MDC.put("traceId","parent");
        try {
            assertThrows(java.io.IOException.class,()->new RequestTraceFilter().doFilter(request,response,(req,res)->{
                assertNotEquals("parent",MDC.get("traceId"));throw new java.io.IOException("test");
            }));
            assertEquals("parent",MDC.get("traceId"));
            assertEquals(36,response.getHeader("X-Request-ID").length());
        } finally {MDC.clear();}
    }
}
