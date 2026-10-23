package io.metersphere.system.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseWorkloadFilterTests {
    @Test void rejectsBeforeDatabaseAndReleasesAfterFailure() throws Exception {
        var filter=new DatabaseWorkloadFilter(1);
        var request=new MockHttpServletRequest("POST","/api/mcp");
        request.setAttribute("requestId","trace-test");
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{
            assertEquals(1,filter.activeRequests());
            var rejected=new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("POST","/api/mcp"),rejected,(r,s)->fail("must not authenticate"));
            assertEquals(503,rejected.getStatus());
            assertEquals("3",rejected.getHeader("Retry-After"));
            assertFalse(rejected.getContentAsString().contains("Exception"));
        });
        assertEquals(0,filter.activeRequests());
        assertEquals(1,filter.rejectedRequests());
        assertThrows(jakarta.servlet.ServletException.class,()->filter.doFilter(new MockHttpServletRequest("POST","/mcp"),new MockHttpServletResponse(),(r,s)->{throw new jakarta.servlet.ServletException("test");}));
        assertEquals(0,filter.activeRequests());
    }

    @Test void readinessBypassesHeavyTrafficAndAsyncCompletionReleasesOnce() throws Exception {
        var filter=new DatabaseWorkloadFilter(1);
        var request=new MockHttpServletRequest("POST","/mcp");request.setAsyncSupported(true);
        filter.doFilter(request,new MockHttpServletResponse(),(r,s)->request.startAsync());
        assertEquals(1,filter.activeRequests());
        var rejected=new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST","/api/ai/case-imports/precheck"),rejected,(r,s)->fail("precheck must be admitted before parsing"));
        assertEquals(503,rejected.getStatus());
        filter.doFilter(new MockHttpServletRequest("GET","/agent/v1/functional/health"),new MockHttpServletResponse(),(r,s)->{});
        request.getAsyncContext().complete();
        assertEquals(0,filter.activeRequests());
    }
}
