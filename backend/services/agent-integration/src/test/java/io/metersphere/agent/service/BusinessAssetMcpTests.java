package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.*;
import io.metersphere.agent.tool.*;
import io.metersphere.system.domain.AgentToken;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

class BusinessAssetMcpTests {
  @AfterEach
  void clear() {
    AgentTokenContext.clear();
  }

  @Test
  void discoveryAndCallUseResourceOwnedKeysWithoutInventingGenericRequestId() {
    var executor = mock(ExternalExecutorService.class);
    when(executor.connect("p", "executor", "stable")).thenReturn(Map.of("sessionId", "session"));
    var handler = new BuiltinAgentMcpToolConfig().externalExecutorConnectTool(executor);
    var registry = new AgentMcpToolRegistry(List.of(handler));
    var transport = new AgentMcpStreamableService();
    var cache = mock(AgentIdempotencyService.class);
    var limiter = mock(AgentTokenRateLimiter.class);
    when(limiter.tryAcquireTool(anyString(), anyString())).thenReturn(true);
    ReflectionTestUtils.setField(transport, "agentMcpToolRegistry", registry);
    ReflectionTestUtils.setField(transport, "safeErrorMapper", new AgentSafeErrorMapper());
    ReflectionTestUtils.setField(transport, "agentTokenRateLimiter", limiter);
    ReflectionTestUtils.setField(transport, "agentIdempotencyService", cache);
    var token = new AgentToken();
    token.setId("token");
    token.setScopes(AgentTokenScope.BUSINESS_ASSET_WRITE);
    AgentTokenContext.set(token);
    Map<?, ?> schema =
        ReflectionTestUtils.invokeMethod(transport, "schemaWithIdempotency", handler);
    assertFalse(((Map<?, ?>) schema.get("properties")).containsKey("requestId"));
    var request =
        Map.<String, Object>of(
            "jsonrpc",
            "2.0",
            "id",
            1,
            "method",
            "tools/call",
            "params",
            Map.of(
                "name",
                handler.name(),
                "arguments",
                Map.of("projectId", "p", "executorId", "executor", "requestKey", "stable")));
    assertTrue(transport.handle(request).containsKey("result"));
    verify(executor).connect("p", "executor", "stable");
    verifyNoInteractions(cache);
    assertThrows(
        BusinessGovernanceException.class,
        () -> handler.execute(Map.of("projectId", "p", "executorId", 42, "requestKey", "stable")));
  }

  @Test
  void legacyWildcardDoesNotAcquireCredentialOrBusinessGovernanceGrants() {
    assertFalse(
        AgentTokenScopeParser.hasScope(Set.of("AGENT_ALL"), AgentTokenScope.BUSINESS_ASSET_WRITE));
    assertFalse(
        AgentTokenScopeParser.hasScope(
            Set.of("AGENT_ALL"), AgentTokenScope.SHARED_CONFIGURATION_RESOLVE));
    assertTrue(
        AgentTokenScopeParser.hasScope(
            Set.of(AgentTokenScope.BUSINESS_ASSET_WRITE), AgentTokenScope.BUSINESS_ASSET_WRITE));
  }
}
