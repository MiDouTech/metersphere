package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionCreateRequest;
import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionPreflightDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;

import java.util.List;
import java.util.Map;
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
    @Test
    void personalPlanExecutionPinsPublishedVersionForEachMatchingRelation() {
        var service = new AgentExecutionService();
        var plans = Mockito.mock(AgentTestPlanQueryService.class);
        ReflectionTestUtils.setField(service, "agentTestPlanQueryService", plans);
        var task = new AgentExecutionTaskDTO();
        task.setId("task");
        task.setProjectId("project");
        task.setTestPlanId("plan");
        task.setTaskOrigin("PERSONAL_MCP");
        task.setExecutorChannel("EXTERNAL_MCP_AGENT");
        task.setEnvironmentProfileVersion(1);
        var item = new AgentExecutionCaseDTO();
        item.setId("execution-case");
        item.setCaseId("case");
        item.setCaseVersion("source");
        item.setTestPlanCaseId("relation");
        var preflight = new AgentExecutionPreflightDTO();
        preflight.setOriginalCaseIds(List.of("case"));
        preflight.setAddedCaseIds(List.of());
        task.setEnvironmentId("env");
        var runtime = new io.metersphere.agent.dto.AgentExecutionStepDTO();
        runtime.setId("runtime");
        runtime.setExecutionCaseId(item.getId());
        runtime.setPos(1);
        runtime.setInstruction("Inspect the current state");
        runtime.setExpected("legacy value");
        Mockito.when(plans.executionContractVersions("project", List.of(item))).thenReturn(Map.of("case", 3L));
        Mockito.when(plans.publishedContractForSnapshot("project", "case", "source", 3L)).thenReturn(Map.of(
                "environmentId", "env", "steps", List.of(Map.of("id", "S1", "sourcePosition", 1)),
                "assertions", List.of(Map.of("id", "A1", "stepId", "S1", "required", true,
                        "verificationMethod", "AUTHORIZED_QUERY", "expected", "published value"))));

        String json = ReflectionTestUtils.invokeMethod(service, "buildExecutionContract",
                task, List.of(item), List.of(runtime), preflight, "hash");
        var contract = AgentProtocolData.object(io.metersphere.sdk.util.JSON.parseObject(json));
        var frozen = AgentProtocolData.objects(contract.get("cases")).getFirst();
        Assertions.assertEquals("relation", frozen.get("testPlanCaseId"));
        Assertions.assertEquals("source", frozen.get("structuredContractSourceVersion"));
        Assertions.assertEquals(3L, ((Number) frozen.get("structuredContractVersion")).longValue());
        var step = AgentProtocolData.objects(frozen.get("steps")).getFirst();
        var assertion = AgentProtocolData.objects(step.get("assertions")).getFirst();
        Assertions.assertEquals("published value", assertion.get("expected"));

        task.setRequiredCapabilities("[\"ASSERTION_V2\"]");
        String typedJson = ReflectionTestUtils.invokeMethod(service, "buildExecutionContract",
                task, List.of(item), List.of(runtime), preflight, "hash");
        var typedContract = AgentProtocolData.object(io.metersphere.sdk.util.JSON.parseObject(typedJson));
        var typedCase = AgentProtocolData.objects(typedContract.get("cases")).getFirst();
        var typedStep = AgentProtocolData.objects(typedCase.get("steps")).getFirst();
        var typedAssertion = AgentProtocolData.objects(typedStep.get("assertions")).getFirst();
        Assertions.assertEquals("A1", typedAssertion.get("assertionId"));
        Assertions.assertEquals("STRING", typedAssertion.get("valueType"));
        Assertions.assertEquals("published value", typedAssertion.get("expected"));

        task.setEnvironmentId("different-environment");
        String heldJson = ReflectionTestUtils.invokeMethod(service, "buildExecutionContract",
                task, List.of(item), List.of(runtime), preflight, "hash");
        var held = AgentProtocolData.object(io.metersphere.sdk.util.JSON.parseObject(heldJson));
        var heldCase = AgentProtocolData.objects(held.get("cases")).getFirst();
        Assertions.assertEquals("PROTOCOL_ENVIRONMENT_MISMATCH", heldCase.get("structuredContractIssue"));
        var heldStep = AgentProtocolData.objects(heldCase.get("steps")).getFirst();
        Assertions.assertEquals(List.of(), heldStep.get("assertions"));
    }
    @Test
    void taskListUsesExactPlanFilterForBothRowsAndCount() {
        var service = new AgentExecutionService();
        var mapper = Mockito.mock(io.metersphere.agent.mapper.AgentExecutionMapper.class);
        var projects = Mockito.mock(AgentProjectService.class);
        Mockito.when(projects.resolveProjectId("project")).thenReturn("project");
        ReflectionTestUtils.setField(service, "agentExecutionMapper", mapper);
        ReflectionTestUtils.setField(service, "agentProjectService", projects);
        var request = new io.metersphere.agent.dto.AgentExecutionTaskSearchRequest();
        request.setProjectId("project");
        request.setTestPlanId("  plan-1  ");
        service.searchTasks(request);

        Mockito.verify(mapper).countTasks("project", "plan-1", "", "", null, null, null, null, null);
        Mockito.verify(mapper).searchTasks("project", "plan-1", "", "", null, null, null, null, null, 0, 20);
    }
}
