package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolRegistry;
import io.metersphere.system.domain.AgentToken;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedConfigurationMcpToolsTests {
    SharedConfigurationService configurations;
    Map<String, AgentMcpToolHandler> tools;
    @BeforeEach void setup() throws Exception {
        configurations = mock(SharedConfigurationService.class);
        Class<?> type = Class.forName("io.metersphere.agent.tool.configuration.SharedConfigurationMcpTools");
        Object config = type.getConstructor(SharedConfigurationService.class, Validator.class)
                .newInstance(configurations, Validation.buildDefaultValidatorFactory().getValidator());
        tools = new LinkedHashMap<>();
        for (var method : type.getDeclaredMethods()) if (method.isAnnotationPresent(Bean.class)) {
            AgentMcpToolHandler handler = (AgentMcpToolHandler) method.invoke(config);
            assertNull(tools.put(handler.name(), handler));
        }
    }
    @AfterEach void clear() { AgentTokenContext.clear(); }

    @Test void allConfigurationOperationsAreRegisteredAndUseExistingService() {
        for (String action : List.of("list", "read", "save", "batch", "versions", "select_version", "retry_parse", "resolve"))
            assertTrue(tools.containsKey("metersphere.shared_configuration." + action));
        when(configurations.list("project")).thenReturn(List.of(Map.of("id", "config")));
        assertEquals(List.of(Map.of("id", "config")), tools.get("metersphere.shared_configuration.list").execute(Map.of("projectId", "project")));
        tools.get("metersphere.shared_configuration.save").execute(Map.of("projectId", "project", "systemKey", "system",
                "environmentKey", "test", "name", "fixture", "kind", "PLAIN", "value", "fixture-value", "expectedVersion", 0));
        verify(configurations).saveStructured("project", null, "system", "test", "fixture", "PLAIN", "fixture-value", null, 0, null, false);
    }

    @Test void resolveAndSaveBypassGenericCacheEvenWithAnIdempotencyHeader() {
        AgentToken token = new AgentToken(); token.setId("token"); token.setUserId("actor"); token.setScopes("FUNCTIONAL_READ");
        AgentTokenContext.set(token);
        var rate = mock(io.metersphere.agent.security.AgentTokenRateLimiter.class);
        when(rate.tryAcquireTool(anyString(), anyString())).thenReturn(true);
        var cache = mock(AgentIdempotencyService.class);
        var rpc = new AgentMcpStreamableService();
        org.springframework.test.util.ReflectionTestUtils.setField(rpc, "runtimeRoleAccess", org.mockito.Mockito.mock(io.metersphere.agent.service.AgentRuntimeRoleAccess.class));
        ReflectionTestUtils.setField(rpc, "agentMcpToolRegistry", new AgentMcpToolRegistry(new ArrayList<>(tools.values())));
        ReflectionTestUtils.setField(rpc, "agentTokenRateLimiter", rate);
        ReflectionTestUtils.setField(rpc, "agentIdempotencyService", cache);
        ReflectionTestUtils.setField(rpc, "safeErrorMapper", new AgentSafeErrorMapper());
        when(configurations.resolveForExecutor("project", "session", "config")).thenReturn(Map.of("value", "synthetic-private-fixture"));
        var response = rpc.handle(Map.of("id", 1, "method", "tools/call", "params", Map.of("name",
                "metersphere.shared_configuration.resolve", "arguments", Map.of("projectId", "project", "sessionId", "session", "id", "config"))), "stable-retry");
        assertFalse(response.containsKey("error"));
        assertTrue(response.toString().contains("synthetic-private-fixture"));
        verify(configurations).resolveForExecutor("project", "session", "config");
        verifyNoInteractions(cache);
    }

    @Test void invalidArgumentsAreRejectedBeforeBusinessExecution() {
        assertThrows(io.metersphere.sdk.exception.MSException.class, () -> tools.get("metersphere.shared_configuration.read")
                .execute(Map.of("projectId", "project", "id", "config", "version", 0)));
        assertThrows(io.metersphere.sdk.exception.MSException.class, () -> tools.get("metersphere.shared_configuration.resolve")
                .execute(Map.of("projectId", "project", "id", "config", "sessionId", "session", "unknown", true)));
        verifyNoInteractions(configurations);
    }

    @Test void auditToolUsesCurrentRoleAndForwardsBoundedFilters() {
        var tool = tools.get("metersphere.shared_configuration.audits");
        assertNotNull(tool);
        assertEquals("USER_RBAC", tool.requiredScope());
        assertEquals(true, tool.annotations().get("readOnlyHint"));
        when(configurations.audits("project", "config", 1, 20, null, null))
                .thenReturn(Map.of("items", List.of(), "total", 0L, "page", 1, "pageSize", 20));
        assertEquals(0L, ((Map<?, ?>) tool.execute(Map.of("projectId", "project", "id", "config"))).get("total"));
        tool.execute(Map.of("projectId", "project", "id", "config", "page", 2, "pageSize", 5,
                "eventType", "SELECT_VERSION", "version", 2));
        verify(configurations).audits("project", "config", 2, 5, "SELECT_VERSION", 2);
        assertThrows(io.metersphere.sdk.exception.MSException.class, () -> tool.execute(Map.of("projectId", "project", "id", "config", "pageSize", 101)));
        assertThrows(io.metersphere.sdk.exception.MSException.class, () -> tool.execute(Map.of("projectId", "project", "id", "config", "eventType", "UNKNOWN")));
    }
}
