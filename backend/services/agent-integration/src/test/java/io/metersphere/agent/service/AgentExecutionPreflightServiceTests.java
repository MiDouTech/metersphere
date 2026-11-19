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
