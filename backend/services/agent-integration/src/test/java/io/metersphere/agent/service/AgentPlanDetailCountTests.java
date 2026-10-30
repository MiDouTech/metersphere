package io.metersphere.agent.service;

import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.mapper.TestPlanMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentPlanDetailCountTests {
    @Test void planDetailIncludesRealAssociationCount(){
        var service=new AgentTestPlanWriteService();var mapper=mock(TestPlanMapper.class);var jdbc=mock(JdbcTemplate.class);
        var plan=new TestPlan();plan.setId("plan");plan.setProjectId("p");plan.setName("plan");
        when(mapper.selectByPrimaryKey("plan")).thenReturn(plan);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq("plan"))).thenReturn(3);
        ReflectionTestUtils.setField(service,"testPlanMapper",mapper);ReflectionTestUtils.setField(service,"jdbc",jdbc);ReflectionTestUtils.setField(service,"access",mock(AgentBusinessAccess.class));
        assertEquals(3,service.get("plan").getAssociatedCaseCount());
    }
}
