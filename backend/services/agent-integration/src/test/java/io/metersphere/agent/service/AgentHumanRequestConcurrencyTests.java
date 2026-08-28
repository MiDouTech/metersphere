package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentHumanRequestDTO;
import io.metersphere.agent.mapper.AgentHumanRequestMapper;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class AgentHumanRequestConcurrencyTests {
    @Test void firstRecipientWinsAndSecondResolutionConflicts(){
        AgentHumanRequestService service=new AgentHumanRequestService();AgentHumanRequestMapper mapper=Mockito.mock(AgentHumanRequestMapper.class);JdbcTemplate jdbc=Mockito.mock(JdbcTemplate.class);ReflectionTestUtils.setField(service,"mapper",mapper);ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);
        AgentHumanRequestDTO pending=new AgentHumanRequestDTO();pending.setId("h1");pending.setTaskId("t1");pending.setProjectId("p1");pending.setStatus("PENDING");pending.setResolutionVersion(0);
        AgentHumanRequestDTO approved=new AgentHumanRequestDTO();approved.setId("h1");approved.setTaskId("t1");approved.setProjectId("p1");approved.setStatus("APPROVED");approved.setResolutionVersion(1);
        when(mapper.selectById("h1")).thenReturn(pending,approved,pending);
        when(jdbc.queryForObject(contains("ai_human_request_recipient"),eq(Integer.class),any(Object[].class))).thenReturn(1);
        when(jdbc.update(startsWith("UPDATE ai_execution_human_request"),any(Object[].class))).thenReturn(1,0);
        when(jdbc.queryForList(contains("SELECT user_id"),eq(String.class),any())).thenReturn(List.of("u1","u2","u3"));
        AgentHumanRequestDTO first=service.respondFirstWins("t1","h1","u1","APPROVED","ok",0);Assertions.assertEquals("APPROVED",first.getStatus());
        Assertions.assertEquals("ALREADY_RESOLVED",Assertions.assertThrows(MSException.class,()->service.respondFirstWins("t1","h1","u2","APPROVED","late",0)).getMessage());
    }
}
