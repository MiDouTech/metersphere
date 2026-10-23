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
        ReflectionTestUtils.setField(service, "llmFeature", Mockito.mock(io.metersphere.system.security.LlmFeature.class));
        ReflectionTestUtils.setField(service,"agentProjectService",projects);Mockito.when(projects.resolveProjectId("p1")).thenReturn("p1");AgentExecutionActorContext.bind("u1");
        AgentExecutionCreateRequest request=new AgentExecutionCreateRequest();request.setProjectId("p1");
        MSException error=Assertions.assertThrows(MSException.class,()->service.create(request));
        Assertions.assertEquals("PREFLIGHT_REQUIRED",error.getMessage());
    }

    @Test void disabledPlatformCannotCreateEvenThroughServiceEntry() {
        AgentExecutionService service = new AgentExecutionService();
        ReflectionTestUtils.setField(service, "llmFeature", new io.metersphere.system.security.LlmFeature());
        MSException error = Assertions.assertThrows(MSException.class,
                () -> service.create(new AgentExecutionCreateRequest()));
        Assertions.assertEquals(io.metersphere.system.controller.handler.result.CommonResultCode.FEATURE_DISABLED,
                error.getErrorCode());
    }

    @Test void disabledPlatformDoesNotBlockPersonalMcpPreflightValidation() {
        AgentExecutionService service = new AgentExecutionService();
        ReflectionTestUtils.setField(service, "llmFeature", new io.metersphere.system.security.LlmFeature());
        AgentProjectService projects = Mockito.mock(AgentProjectService.class);
        ReflectionTestUtils.setField(service, "agentProjectService", projects);
        Mockito.when(projects.resolveProjectId("p1")).thenReturn("p1");
        AgentExecutionActorContext.bind("u1");
        AgentExecutionCreateRequest request = new AgentExecutionCreateRequest();
        request.setProjectId("p1");
        Assertions.assertEquals("PREFLIGHT_REQUIRED",
                Assertions.assertThrows(MSException.class, () -> service.createPersonalMcp(request)).getMessage());
    }

    @Test void disabledPlatformCannotResumeOrRetryAndDoesNotMutateTasks() {
        AgentExecutionService service = new AgentExecutionService();
        ReflectionTestUtils.setField(service, "llmFeature", new io.metersphere.system.security.LlmFeature());
        var mapper = Mockito.mock(io.metersphere.agent.mapper.AgentExecutionMapper.class);
        var projects = Mockito.mock(AgentProjectService.class);
        var task = new io.metersphere.agent.dto.AgentExecutionTaskDTO();
        task.setId("t1"); task.setProjectId("p1"); task.setExecutorChannel("MODEL_API_RUNNER");
        Mockito.when(mapper.selectTaskById("t1")).thenReturn(task);
        Mockito.when(projects.resolveProjectId("p1")).thenReturn("p1");
        ReflectionTestUtils.setField(service, "agentExecutionMapper", mapper);
        ReflectionTestUtils.setField(service, "agentProjectService", projects);
        var response = new io.metersphere.agent.dto.AgentHumanResponseRequest(); response.setAction("APPROVE");
        java.util.List<org.junit.jupiter.api.function.Executable> actions = java.util.List.of(
                () -> service.confirm("t1", null), () -> service.loginReady("t1", null),
                () -> service.retry("t1", null), () -> service.respondHumanRequest("t1", "r1", response));
        actions.forEach(action -> Assertions.assertEquals(
                io.metersphere.system.controller.handler.result.CommonResultCode.FEATURE_DISABLED,
                Assertions.assertThrows(MSException.class, action).getErrorCode()));
        Mockito.verify(mapper, Mockito.times(4)).selectTaskById("t1");
        Mockito.verifyNoMoreInteractions(mapper);
    }
}
