package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentModelProfileDTO;
import io.metersphere.agent.service.gateway.GatewayPlanningResponse;
import io.metersphere.agent.service.gateway.MapGatewayClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentExecutionPlanningServiceTests {
    @Test
    void invalidGatewayResponseIsAccountedBeforeSingleRepair(){
        AgentExecutionPlanningService service=new AgentExecutionPlanningService();
        AgentModelProfileService profiles=Mockito.mock(AgentModelProfileService.class);AgentModelInvocationService invocations=Mockito.mock(AgentModelInvocationService.class);
        MapGatewayClient gateway=Mockito.mock(MapGatewayClient.class);AgentWebExecutionContractValidator validator=Mockito.mock(AgentWebExecutionContractValidator.class);
        JdbcTemplate jdbc=Mockito.mock(JdbcTemplate.class);AgentBudgetGuard budget=Mockito.mock(AgentBudgetGuard.class);AgentExecutionPreflightService preflight=Mockito.mock(AgentExecutionPreflightService.class);
        ReflectionTestUtils.setField(service,"profiles",profiles);ReflectionTestUtils.setField(service,"invocations",invocations);ReflectionTestUtils.setField(service,"gateway",gateway);ReflectionTestUtils.setField(service,"validator",validator);ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);ReflectionTestUtils.setField(service,"budgetGuard",budget);ReflectionTestUtils.setField(service,"preflightService",preflight);
        AgentModelProfileDTO profile=new AgentModelProfileDTO();profile.setId("m1");profile.setGatewayAppCaller("tests");profile.setLogicalModelPublicId("model");profile.setGatewayPromptPolicyId("policy");profile.setMaxOutputTokens(1000);profile.setRequestTimeoutMs(10000);
        when(profiles.assertUsable(eq("m1"),eq("p1"),anyList())).thenReturn(profile);when(profiles.serviceKeyRef("m1")).thenReturn("vault://gateway");
        when(jdbc.queryForObject(contains("prompt_template_version_id"),eq(String.class),any())).thenReturn("pv1");
        when(jdbc.queryForObject(contains("trace_id"),eq(String.class),any())).thenReturn("trace1");
        when(jdbc.queryForObject(contains("prompt_template_snapshot"),eq(String.class),any())).thenReturn("{\"systemTemplate\":\"{{instruction}}\",\"businessTemplate\":\"{{expected}}\"}");
        when(jdbc.queryForObject(contains("preflight_id"),eq(String.class),any())).thenReturn("pf1");when(preflight.frozenExecutableAssetContexts("pf1")).thenReturn(List.of());
        when(invocations.start(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn("i1","i2");
        GatewayPlanningResponse invalid=new GatewayPlanningResponse();invalid.setGatewayRequestId("g1");invalid.setStructuredOutput(Map.of("bad",true));invalid.getUsage().setInputTokens(10L);invalid.getCost().setAmount(new java.math.BigDecimal("0.01"));
        GatewayPlanningResponse repaired=new GatewayPlanningResponse();repaired.setGatewayRequestId("g2");repaired.setStructuredOutput(Map.of("action",Map.of("type","NAVIGATE"),"assertions",List.of(Map.of("type","VISIBLE"))));
        when(gateway.invokeStructured(any(),eq("vault://gateway"))).thenReturn(invalid,repaired);
        AgentExecutionStepDTO step=new AgentExecutionStepDTO();step.setId("s1");step.setInstruction("open");step.setExpected("visible");step.setRiskLevel("LOW");
        service.plan("p1","o1","m1","t1","https://example.test",List.of(step),"u1");
        verify(invocations).recordFailure("i1","MAP_GATEWAY_SCHEMA_INVALID",invalid);verify(invocations).finish("i2",repaired);
    }
}
