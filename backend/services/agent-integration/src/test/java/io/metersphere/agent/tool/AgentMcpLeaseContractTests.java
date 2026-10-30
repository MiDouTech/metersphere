package io.metersphere.agent.tool;

import io.metersphere.agent.service.AgentTaskExecutionApplicationService;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentMcpLeaseContractTests {
    @Test void humanAndEventsSchemasExposeEveryRequiredLeaseField(){
        var config=new BuiltinAgentMcpToolConfig();
        var service=mock(AgentTaskExecutionApplicationService.class);
        for(var tool:List.of(config.humanRequestCreateTool(service),config.executionEventsBatchTool(service))){
            var required=(List<?>)tool.inputSchema().get("required");
            var properties=(Map<?,?>)tool.inputSchema().get("properties");
            assertTrue(required.containsAll(List.of("leaseId","leaseToken")));
            assertTrue(properties.keySet().containsAll(required));
            assertEquals("AGENT_VALIDATION_ERROR",assertThrows(MSException.class,()->tool.execute(Map.of())).getMessage());
        }
        verifyNoInteractions(service);
    }
    @Test void validHumanRequestForwardsAllLeaseFields(){
        var service=mock(AgentTaskExecutionApplicationService.class);
        new BuiltinAgentMcpToolConfig().humanRequestCreateTool(service).execute(Map.of("taskId","t","leaseId","l","leaseToken","secret","requestId","r","requestType","INPUT","title","help"));
        verify(service).createHumanRequest(eq("t"),eq("l"),eq("secret"),argThat(r->"r".equals(r.getRequestId())&&"help".equals(r.getTitle())));
    }
    @Test void validEventBatchForwardsLeaseSecret(){
        var service=mock(AgentTaskExecutionApplicationService.class);
        new BuiltinAgentMcpToolConfig().executionEventsBatchTool(service).execute(Map.of("leaseId","l","leaseToken","secret","events",List.of(Map.of("eventType","LOG"))));
        verify(service).appendEvents(eq("l"),eq("secret"),argThat(r->r.getEvents().size()==1));
    }
}
