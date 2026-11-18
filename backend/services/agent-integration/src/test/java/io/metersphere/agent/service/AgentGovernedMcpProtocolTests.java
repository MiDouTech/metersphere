package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.*;
import io.metersphere.agent.tool.*;
import io.metersphere.agent.tool.functional.*;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentGovernedMcpProtocolTests {
    AgentMcpStreamableService transport;AgentProtocolToolService protocol;AgentMcpToolRegistry registry;
    @BeforeEach void fixture(){protocol=mock(AgentProtocolToolService.class);registry=new AgentMcpToolRegistry(List.of(),List.of(new AgentGovernedExecutionTools(protocol)));transport=new AgentMcpStreamableService();ReflectionTestUtils.setField(transport,"agentMcpToolRegistry",registry);ReflectionTestUtils.setField(transport,"safeErrorMapper",new AgentSafeErrorMapper());var limiter=mock(AgentTokenRateLimiter.class);when(limiter.tryAcquireTool(anyString(),anyString())).thenReturn(true);ReflectionTestUtils.setField(transport,"agentTokenRateLimiter",limiter);var token=new AgentToken();token.setId("synthetic-token-record");token.setScopes(AgentProtocolData.encode(List.of(AgentTokenScope.SESSION_READ_V1,AgentTokenScope.SESSION_EXECUTE_V1,AgentTokenScope.KNOWLEDGE_READ_V1,AgentTokenScope.KNOWLEDGE_EDIT_V1)));AgentTokenContext.set(token);}
    @AfterEach void clear(){AgentTokenContext.clear();}
    @Test void nativeInitializeAndToolsListExposeActualRegisteredVersionedProtocol() throws Exception {var initialize=transport.handle(Map.of("jsonrpc","2.0","id",1,"method","initialize","params",Map.of("protocolVersion","2025-03-26")));assertEquals("2025-03-26",AgentProtocolData.object(initialize.get("result")).get("protocolVersion"));var listResponse=transport.handle(Map.of("jsonrpc","2.0","id",2,"method","tools/list"));var listed=AgentProtocolData.objects(AgentProtocolData.object(listResponse.get("result")).get("tools"));String evidence=System.getProperty("protocol.evidence.dir");if(evidence!=null){var dir=java.nio.file.Path.of(evidence);java.nio.file.Files.createDirectories(dir);java.nio.file.Files.writeString(dir.resolve("native-initialize-tools-list.json"),AgentProtocolData.encode(Map.of("environment","isolated in-process native dispatcher, synthetic identity, not deployed evidence","initialize",initialize,"toolsList",listResponse)));}for(String operation:AgentProtocolToolService.OPERATIONS)assertTrue(listed.stream().anyMatch(tool->(AgentSessionProtocolService.TOOL_PREFIX+operation).equals(tool.get("name"))),operation);assertTrue(listed.stream().noneMatch(tool->tool.get("name").toString().endsWith("result.verify")));assertTrue(listed.stream().allMatch(tool->Boolean.FALSE.equals(AgentProtocolData.object(tool.get("inputSchema")).get("additionalProperties"))));}
    @Test void originalProtocolPayloadAndKeyReachServiceWithoutGenericTokenLedgerRewrite(){var args=Map.<String,Object>of("protocolVersion","1.0","idempotencyKey","original-key","sessionId","session","caseExecutionId","instance","expectedContextVersion",7,"leaseGeneration",2,"actionType","CREATE","targetEntity","fixture");when(protocol.execute("action.prepare",args)).thenReturn(Map.of("permissionToAct",false));var result=transport.handle(Map.of("jsonrpc","2.0","id",3,"method","tools/call","params",Map.of("name",AgentSessionProtocolService.TOOL_PREFIX+"action.prepare","arguments",args)),"original-key");assertTrue(result.containsKey("result"));verify(protocol).execute("action.prepare",args);assertFalse(args.containsKey("requestId"));}
    @Test void headerAndBodyConflictIsRejectedBeforeBusinessDispatch(){var result=transport.handle(Map.of("jsonrpc","2.0","id",4,"method","tools/call","params",Map.of("name",AgentSessionProtocolService.TOOL_PREFIX+"step.save","arguments",Map.of("idempotencyKey","body-key"))),"header-key");assertTrue(result.containsKey("error"));verifyNoInteractions(protocol);}
    @Test void legacyWildcardDoesNotAdvertiseNewProtocol(){AgentTokenContext.get().setScopes("[\"*\"]");var tools=AgentProtocolData.objects(AgentProtocolData.object(transport.handle(Map.of("jsonrpc","2.0","id",5,"method","tools/list")).get("result")).get("tools"));assertTrue(tools.isEmpty());}
    @Test void everyNewProtocolWriterHasExplicitResourceOwnedReplayRouting(){for(var handler:registry.all())if(!handler.readOnlyHint())assertEquals(true,ReflectionTestUtils.invokeMethod(AgentMcpStreamableService.class,"bypassesGenericIdempotency",handler.name()),handler.name());}
}
