package io.metersphere.agent.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

class AgentMcpStreamableServiceTests {

    private AgentMcpStreamableService service;

    @BeforeEach
    void setUp() {
        // isNotification / handleNotification 不依赖注入字段
        service = new AgentMcpStreamableService();
        ReflectionTestUtils.setField(service, "safeErrorMapper", new AgentSafeErrorMapper());
    }

    @Test
    void directTriggerToolCallIsAlwaysForbiddenForPersonalMcp() {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", 7);
        request.put("method", "tools/call");
        request.put("params", Map.of("name", "metersphere.execution.trigger.fire", "arguments", Map.of()));
        Map<String, Object> response = service.handle(request);
        Map<?, ?> error = (Map<?, ?>) response.get("error");
        Map<?, ?> data = (Map<?, ?>) error.get("data");
        Assertions.assertEquals("MCP_TOOL_FORBIDDEN", data.get("code"));
    }

    @Test
    void notificationWithoutIdShouldBeDetected() {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "notifications/initialized");
        Assertions.assertTrue(service.isNotification(request));
    }

    @Test
    void requestWithIdShouldNotBeNotification() {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "notifications/initialized");
        Assertions.assertFalse(service.isNotification(request));
    }

    @Test
    void handleNotificationInitializedShouldNoop() {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "notifications/initialized");
        Assertions.assertDoesNotThrow(() -> service.handleNotification(request));
    }

    @Test
    void leaseResponseIsNeverStoredOrReplayedByGenericCache() {
        var token = new io.metersphere.system.domain.AgentToken();
        token.setId("test-token"); token.setScopes("AGENT_ALL");
        io.metersphere.agent.security.AgentTokenContext.set(token);
        try {
            var limiter = org.mockito.Mockito.mock(io.metersphere.agent.security.AgentTokenRateLimiter.class);
            org.mockito.Mockito.when(limiter.tryAcquireTool(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
            var cache = org.mockito.Mockito.mock(AgentIdempotencyService.class);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var handler = new io.metersphere.agent.tool.AgentMcpToolHandler() {
                public String name() { return "metersphere.task.claim"; }
                public String requiredScope() { return "TASK_CLAIM"; }
                public Map<String,Object> inputSchema() { return Map.of(); }
                public Map<String,Object> annotations() { return Map.of("readOnlyHint",false); }
                public Object execute(Map<String,Object> args) { return Map.of("leaseToken","fixture-"+calls.incrementAndGet()); }
            };
            ReflectionTestUtils.setField(service,"agentTokenRateLimiter",limiter);
            ReflectionTestUtils.setField(service,"agentIdempotencyService",cache);
            ReflectionTestUtils.setField(service,"agentMcpToolRegistry",new io.metersphere.agent.tool.AgentMcpToolRegistry(java.util.List.of(handler)));
            var request=Map.<String,Object>of("id",1,"method","tools/call","params",Map.of("name",handler.name(),"arguments",Map.of("requestId","claim-key")));
            Assertions.assertTrue(service.handle(request).containsKey("result"));
            Assertions.assertTrue(service.handle(request).containsKey("result"));
            Assertions.assertEquals(2,calls.get());
            org.mockito.Mockito.verifyNoInteractions(cache);
        } finally { io.metersphere.agent.security.AgentTokenContext.clear(); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"metersphere.functional.attachment.upload.complete","metersphere.functional.import.commit","metersphere.functional.archive.prepare","metersphere.functional.snapshot.create","metersphere.functional.case.delete_preview"})
    void durableResourceProtocolsReachCurrentAuthorizationWithoutGenericRequestKey(String name) {
        var token=new io.metersphere.system.domain.AgentToken();token.setId("test-token");token.setScopes("CASE_ATTACHMENT");
        io.metersphere.agent.security.AgentTokenContext.set(token);
        try {
            var limiter=org.mockito.Mockito.mock(io.metersphere.agent.security.AgentTokenRateLimiter.class);
            org.mockito.Mockito.when(limiter.tryAcquireTool(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
            var cache=org.mockito.Mockito.mock(AgentIdempotencyService.class);
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            var handler=new io.metersphere.agent.tool.AgentMcpToolHandler() {
                public String name(){return name;}
                public String requiredScope(){return "CASE_ATTACHMENT";}
                public Map<String,Object> inputSchema(){return Map.of();}
                public Map<String,Object> annotations(){return Map.of("readOnlyHint",false);}
                public Object execute(Map<String,Object> args){return Map.of("checked",calls.incrementAndGet());}
            };
            ReflectionTestUtils.setField(service,"agentTokenRateLimiter",limiter);
            ReflectionTestUtils.setField(service,"agentIdempotencyService",cache);
            ReflectionTestUtils.setField(service,"agentMcpToolRegistry",new io.metersphere.agent.tool.AgentMcpToolRegistry(java.util.List.of(handler)));
            var request=Map.<String,Object>of("id",1,"method","tools/call","params",Map.of("name",name,"arguments",Map.of("uploadId","resource")));
            Assertions.assertTrue(service.handle(request).containsKey("result"));
            Assertions.assertTrue(service.handle(request).containsKey("result"));
            token.setScopes("FUNCTIONAL_READ");
            Assertions.assertTrue(service.handle(request).containsKey("error"));
            Assertions.assertEquals(2,calls.get());org.mockito.Mockito.verifyNoInteractions(cache);
        } finally {io.metersphere.agent.security.AgentTokenContext.clear();}
    }
}
