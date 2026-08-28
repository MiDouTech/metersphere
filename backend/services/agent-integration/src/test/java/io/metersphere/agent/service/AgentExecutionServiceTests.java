package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionCreateRequest;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class AgentExecutionServiceTests {
    @AfterEach void clear(){AgentExecutionActorContext.clear();}
    @Test void createCannotBypassPreflight(){
        AgentExecutionService service=new AgentExecutionService();AgentProjectService projects=Mockito.mock(AgentProjectService.class);
        ReflectionTestUtils.setField(service,"agentProjectService",projects);Mockito.when(projects.resolveProjectId("p1")).thenReturn("p1");AgentExecutionActorContext.bind("u1");
        AgentExecutionCreateRequest request=new AgentExecutionCreateRequest();request.setProjectId("p1");
        MSException error=Assertions.assertThrows(MSException.class,()->service.create(request));
        Assertions.assertEquals("PREFLIGHT_REQUIRED",error.getMessage());
    }
}
