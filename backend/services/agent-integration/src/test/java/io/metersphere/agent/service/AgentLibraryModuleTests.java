package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenScopeParser;
import io.metersphere.functional.domain.FunctionalCaseModule;
import io.metersphere.functional.mapper.FunctionalCaseModuleMapper;
import io.metersphere.functional.service.FunctionalCaseModuleService;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentLibraryModuleTests {
    @Test void versionedGrantIsNeverInheritedFromWildcard() {
        assertFalse(AgentTokenScopeParser.hasScope(Set.of("AGENT_ALL"),AgentTokenScope.CASE_MODULE_WRITE_V2));
        assertFalse(AgentTokenScopeParser.hasScope(Set.of("FUNCTIONAL_ALL"),AgentTokenScope.CASE_MODULE_WRITE_V2));
        assertTrue(AgentTokenScopeParser.hasScope(Set.of("AGENT_ALL",AgentTokenScope.CASE_MODULE_WRITE_V2),AgentTokenScope.CASE_MODULE_WRITE_V2));
        assertTrue(AgentTokenScopeParser.hasScope(Set.of("AGENT_ALL"),AgentTokenScope.FUNCTIONAL_READ));
    }
    @Test void moveRejectsDescendantAndForeignParentBeforeWriting() {
        var service=new FunctionalCaseModuleService();
        var mapper=mock(FunctionalCaseModuleMapper.class);
        var jdbc=mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service,"functionalCaseModuleMapper",mapper);
        ReflectionTestUtils.setField(service,"moduleJdbc",jdbc);
        var source=module("a","p","NONE");
        when(mapper.selectByPrimaryKey("a")).thenReturn(source);
        when(jdbc.queryForList(anyString(),eq(String.class),eq("p"))).thenReturn(List.of("p"));
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),eq("a"))).thenReturn(List.of(source));
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),eq("child"))).thenReturn(List.of(module("child","p","a")));
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),eq("foreign"))).thenReturn(List.of(module("foreign","q","NONE")));
        assertThrows(MSException.class,()->service.moveToParent("a","child","u"));
        assertThrows(MSException.class,()->service.moveToParent("a","foreign","u"));
        assertThrows(MSException.class,()->service.moveToParent("a","a","u"));
        verify(mapper,never()).updateByPrimaryKeySelective(any());
    }
    private FunctionalCaseModule module(String id,String project,String parent) {
        var result=new FunctionalCaseModule();result.setId(id);result.setProjectId(project);result.setParentId(parent);result.setUpdateTime(1L);return result;
    }
}
