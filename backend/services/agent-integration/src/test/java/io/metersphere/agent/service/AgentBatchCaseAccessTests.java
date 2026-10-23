package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentBatchCaseAccessTests {
    @Test void validatesAllCasesInOneProjectScopedQuery() {
        var jdbc=mock(JdbcTemplate.class);var access=new AgentBusinessAccess(jdbc);
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(3L);
        assertDoesNotThrow(()->access.cases("p",List.of("a","b","c")));
        verify(jdbc,times(1)).queryForObject(contains("project_id=? AND deleted=0 AND id IN (?,?,?)"),eq(Long.class),eq("p"),eq("a"),eq("b"),eq("c"));
    }
    @Test void rejectsMissingDeletedOrForeignCasesAndDuplicateInput() {
        var jdbc=mock(JdbcTemplate.class);var access=new AgentBusinessAccess(jdbc);
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(1L);
        assertThrows(MSException.class,()->access.cases("p",List.of("a","foreign")));
        assertThrows(MSException.class,()->access.cases("p",List.of("a","a")));
    }
}
