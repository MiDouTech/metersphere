package io.metersphere.bug.service;

import io.metersphere.bug.domain.Bug;
import io.metersphere.bug.mapper.BugMapper;
import io.metersphere.bug.dto.request.BugTransitionRequest;
import io.metersphere.bug.dto.response.BugTransitionDTO;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BugTransitionFreshResponseTests {
    @Test void responseReloadsAfterJdbcMutationInvalidatesCachedState(){
        var service=spy(new BugWorkflowRuntimeService());var mapper=mock(BugMapper.class);var jdbc=mock(JdbcTemplate.class);var session=mock(SqlSessionTemplate.class);
        ReflectionTestUtils.setField(service,"bugMapper",mapper);ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);ReflectionTestUtils.setField(service,"sqlSessionTemplate",session);
        var bug=new Bug();bug.setId("b");bug.setProjectId("p");bug.setStatus("old");bug.setUpdateTime(1L);bug.setPlatform("Local");bug.setWorkflowId("w");bug.setWorkflowVersion(1);
        when(mapper.selectByPrimaryKey("b")).thenReturn(bug);
        var cacheCleared=new AtomicBoolean();doAnswer(call->{cacheCleared.set(true);return null;}).when(session).clearCache();
        doAnswer(call->{
            var result=new BugTransitionDTO();var current=new BugTransitionDTO.Status();current.setId(cacheCleared.get()?"new":"old");result.setCurrentStatus(current);result.setUpdateTime(cacheCleared.get()?2L:1L);
            var transition=new BugTransitionDTO.Transition();transition.setTransitionId("edge");var target=new BugTransitionDTO.Status();target.setId("new");transition.setTargetStatus(target);transition.setOperable(true);transition.setOverrideRequired(false);result.setTransitions(List.of(transition));return result;
        }).when(service).getTransitions("b");
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq("new"),eq("w"))).thenReturn(false);
        when(jdbc.update(startsWith("UPDATE bug"),any(Object[].class))).thenReturn(1);
        var request=new BugTransitionRequest();request.setTransitionId("edge");request.setTargetStatusId("new");request.setExpectedUpdateTime(1L);
        try(var users=mockStatic(SessionUtils.class);var ids=mockStatic(io.metersphere.system.uid.IDGenerator.class)){
            ids.when(io.metersphere.system.uid.IDGenerator::nextStr).thenReturn("history-id");
            users.when(()->SessionUtils.hasPermission(null,"p","PROJECT_BUG:READ+UPDATE")).thenReturn(true);
            users.when(SessionUtils::getUserId).thenReturn("u");
            var result=service.transition("b",request);assertEquals("new",result.getCurrentStatus().getId());assertEquals(2L,result.getUpdateTime());
            verify(session).clearCache();
        }
    }
}
