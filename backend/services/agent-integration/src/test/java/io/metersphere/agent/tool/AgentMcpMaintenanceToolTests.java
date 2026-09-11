package io.metersphere.agent.tool;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentBatchSubmitResponse;
import io.metersphere.agent.security.*;
import io.metersphere.agent.service.*;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentMcpMaintenanceToolTests {
    private AgentMcpMaintenanceService maintenance;
    private AgentMcpStreamableService transport;
    private AgentIdempotencyService idempotency;
    private List<AgentMcpToolHandler> handlers;
    @BeforeEach void before(){
        maintenance=mock(AgentMcpMaintenanceService.class);var config=new BuiltinAgentMcpToolConfig();
        handlers=List.of(config.functionalBatchSubmitTool(maintenance),config.executionHistoryTool(maintenance),config.executionCheckpointResumeTool(maintenance),
                config.bugTransitionsTool(maintenance),config.bugTransitionTool(maintenance),config.testPlanUpdateTool(maintenance),config.testPlanDisassociateTool(maintenance),config.testPlanGetTool(maintenance));
        transport=new AgentMcpStreamableService();var rate=mock(AgentTokenRateLimiter.class);when(rate.tryAcquireTool(anyString(),anyString())).thenReturn(true);
        idempotency=mock(AgentIdempotencyService.class);
        ReflectionTestUtils.setField(transport,"agentMcpToolRegistry",new AgentMcpToolRegistry(handlers));
        ReflectionTestUtils.setField(transport,"agentTokenRateLimiter",rate);ReflectionTestUtils.setField(transport,"agentIdempotencyService",idempotency);
        ReflectionTestUtils.setField(transport,"safeErrorMapper",new AgentSafeErrorMapper());
        AgentToken token=new AgentToken();token.setId("token");token.setUserId("user");token.setScopes(AgentTokenScope.AGENT_ALL);AgentTokenContext.set(token);
    }
    @AfterEach void after(){AgentTokenContext.clear();}
    @Test void listIncludesAllNewToolsAndRespectsReadScope(){
        var response=transport.handle(Map.of("id",1,"method","tools/list"));
        assertEquals(8,((List<?>)((Map<?,?>)response.get("result")).get("tools")).size());
        AgentTokenContext.get().setScopes(AgentTokenScope.BUG_READ);
        response=transport.handle(Map.of("id",2,"method","tools/list"));
        var tools=(List<?>)((Map<?,?>)response.get("result")).get("tools");assertEquals(1,tools.size());
        assertEquals("metersphere.bug.transitions",((Map<?,?>)tools.getFirst()).get("name"));
    }
    @Test void batchCallReturnsStructuredResultAndReplaySkipsBusinessService(){
        var args=Map.<String,Object>of("projectId","project","requestId","request","results",List.of(Map.of("caseId","case","lastExecResult","SUCCESS")));
        AgentBatchSubmitResponse result=new AgentBatchSubmitResponse();result.setSuccess(1);result.setTotal(1);
        when(maintenance.batch(any(),eq("request"))).thenReturn(result);
        Map<String,Object> request=call("metersphere.functional.submit.batch",args);
        var first=transport.handle(request);assertNotNull(first.get("result"));
        @SuppressWarnings("unchecked") Map<String,Object> cached=(Map<String,Object>)first.get("result");
        when(idempotency.findCachedResponse(anyString(),eq("request"),anyMap())).thenReturn(Optional.of(cached));
        assertEquals(first,transport.handle(request));verify(maintenance,times(1)).batch(any(),eq("request"));
        AgentTokenContext.get().setScopes(AgentTokenScope.BUG_READ);
        assertNotNull(transport.handle(request).get("error"));verify(maintenance,times(1)).batch(any(),eq("request"));
    }
    @Test void missingIdempotencyKeyRejectsWriteBeforeService(){
        assertNotNull(transport.handle(call("metersphere.test_plan.disassociate_cases",Map.of("projectId","project","testPlanId","plan","associationIds",List.of("r")))).get("error"));
        verifyNoInteractions(maintenance);
    }
    private Map<String,Object> call(String name,Map<String,Object> args){return Map.of("id",1,"method","tools/call","params",Map.of("name",name,"arguments",args));}
}
