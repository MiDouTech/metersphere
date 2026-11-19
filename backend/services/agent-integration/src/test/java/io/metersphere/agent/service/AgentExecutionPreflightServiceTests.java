package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionCreateRequest;
import io.metersphere.agent.dto.AgentExecutionPreflightDTO;
import io.metersphere.agent.dto.AgentExecutionPreflightRequest;
import io.metersphere.sdk.util.JSON;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class AgentExecutionPreflightServiceTests {
    @Test
    void handoffScopeMustMatchTheDeliveredReviewAndLivePlanMembership() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        var service = new AgentExecutionPreflightService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "testPlanService", Mockito.mock(AgentTestPlanQueryService.class));
        var approved = new io.metersphere.functional.handoff.HandoffPolicy.Snapshot("PREPARED", "COMPLETED",
                true, true, "generation", "generation", 1, 1, 1, false,
                Map.of("c1", 1L, "c2", 1L), Map.of(), Map.of(), "opinion", "opinion");
        var source = new io.metersphere.functional.handoff.HandoffSourceReader.Source(
                "p1", "plan", "review", "P1", "Plan", "R1", "Review", 1, "fingerprint",
                approved, "snapshot", "rule", "hash");
        when(jdbc.queryForList(Mockito.contains("FROM hifox_http_delivery"),
                Mockito.eq("p1"), Mockito.eq("plan"), Mockito.eq("event")))
                .thenReturn(List.of(Map.of("snapshot_json", JSON.toJSONString(source), "source_changed", false,
                        "generation", "generation")));
        when(jdbc.queryForList(Mockito.contains("FROM test_plan_functional_case"), Mockito.eq(String.class),
                Mockito.eq("plan"))).thenReturn(List.of("c1", "c2"), List.of("c1", "c3"));
        service.assertHandoffScope("p1", "plan", "event", List.of("c2", "c1"));
        Assertions.assertEquals("PREFLIGHT_PLAN_SCOPE_CHANGED", Assertions.assertThrows(
                io.metersphere.sdk.exception.MSException.class,
                () -> service.assertHandoffScope("p1", "plan", "event", List.of("c1", "c2"))).getMessage());
        Assertions.assertEquals("PREFLIGHT_HANDOFF_SCOPE_INVALID", Assertions.assertThrows(
                io.metersphere.sdk.exception.MSException.class,
                () -> service.assertHandoffScope("p1", "plan", "another-event", List.of("c1", "c2"))).getMessage());
    }

    @Test
    void personalPlanCanRun120CasesWithoutChangingOtherExecutionLimits() {
        Assertions.assertTrue(AgentExecutionPreflightService.caseLimit("PERSONAL_MCP", "plan") >= 120);
        Assertions.assertEquals(100, AgentExecutionPreflightService.caseLimit("PLATFORM_SCHEDULED", "plan"));
        Assertions.assertEquals(100, AgentExecutionPreflightService.caseLimit("PERSONAL_MCP", null));
    }

    @Test
    void validatedPreflightOverwritesMutableCreatePolicyAndCapabilities(){
        AgentExecutionPreflightService service=Mockito.spy(new AgentExecutionPreflightService());
        JdbcTemplate jdbc=Mockito.mock(JdbcTemplate.class);ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);
        AgentExecutionPreflightDTO dto=new AgentExecutionPreflightDTO();dto.setId("pf1");dto.setProjectId("p1");dto.setStatus("PASSED");dto.setPromptTemplateVersionId("pv1");dto.setResolvedCaseIds(List.of("c1","c2"));
        Mockito.doReturn(dto).when(service).get("pf1");
        AgentExecutionPreflightRequest frozen=new AgentExecutionPreflightRequest();frozen.setEnvironmentProfileId("env1");frozen.setCredentialReferenceId("cred1");frozen.setModelProfileId("m1");frozen.setPromptTemplateId("prompt1");frozen.setTestPlanId("plan1");frozen.setCaseIds(List.of("c1"));frozen.setRequiredCapabilities(List.of("BROWSER","SCREENSHOT"));frozen.setPolicy(Map.of("scopeExpansionLimit",0.15,"approvalPolicy",Map.of("highRisk","REVIEW")));
        when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(Map.of("actor_id","u1","task_origin","PLATFORM_MANUAL","status","PASSED","expires_at",System.currentTimeMillis()+60000,"request_json",JSON.toJSONString(frozen),"snapshot_json","{\"additionalAssets\":[]}"));
        AgentExecutionCreateRequest create=new AgentExecutionCreateRequest();create.setEnvironmentProfileId("env1");create.setCredentialReferenceId("cred1");create.setModelProfileId("m1");create.setPromptTemplateVersionId("pv1");create.setTestPlanId("plan1");create.setCaseIds(List.of("c1"));create.setRequiredCapabilities(List.of("UNSAFE"));create.setPolicySnapshot("{\"scopeExpansionLimit\":1}");
        service.validateForCreate("pf1","p1","u1","PLATFORM_MANUAL",create);
        Assertions.assertEquals(List.of("BROWSER","SCREENSHOT"),create.getRequiredCapabilities());
        Assertions.assertEquals(List.of("c1","c2"),create.getCaseIds());
        Assertions.assertEquals("0.15",String.valueOf(JSON.parseObject(create.getPolicySnapshot(),Map.class).get("scopeExpansionLimit")));
        Assertions.assertTrue(create.getApprovalPolicy().contains("REVIEW"));
    }
}
