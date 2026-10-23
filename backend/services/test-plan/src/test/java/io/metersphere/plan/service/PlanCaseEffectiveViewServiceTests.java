package io.metersphere.plan.service;

import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.metersphere.functional.service.FunctionalExecutionViewProvider;
import io.metersphere.plan.dto.response.TestPlanCaseDetailResponse;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlanCaseEffectiveViewServiceTests {
    @Test
    void currentFactsStaySeparateFromDefinitionAndKeepAllSubmittedSteps() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FunctionalExecutionViewProvider provider = mock(FunctionalExecutionViewProvider.class);
        var source = new FunctionalCaseDetailDTO();
        source.setId("case-1");
        source.setLastExecuteResult("ERROR");
        source.setLastExecuteTime(42L);
        source.setLastExecuteUser("executor");
        var facts = Map.<String, Object>of("steps", List.of(Map.of("step", 2), Map.of("step", 1), Map.of("step", 2)));
        when(provider.current(source)).thenReturn(Optional.of(new FunctionalExecutionViewProvider.View("DIRECT_SUBMISSION", "submission-1", facts)));
        var response = new TestPlanCaseDetailResponse();
        new PlanCaseEffectiveViewService(jdbc, List.of(provider)).enrich(response, source);
        assertEquals("ERROR", response.getLastExecuteResult());
        assertEquals(42L, response.getLastExecuteTime());
        assertEquals("executor", response.getLastExecuteUser());
        assertEquals("submission-1", response.getExecutionRef().get("id"));
        assertSame(facts, response.getExecutionFacts());
        assertNull(response.getSteps());
    }

    @Test
    void sourceRelationsCannotBeRemovedFromPlanButManualRelationsCan() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("source"))).thenReturn(1L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("manual"))).thenReturn(0L);
        var service = new PlanCaseEffectiveViewService(jdbc, List.of());
        assertThrows(MSException.class, () -> service.requireManualBugRelation("source"));
        assertDoesNotThrow(() -> service.requireManualBugRelation("manual"));
    }

    @Test
    void ordinaryExecutionDoesNotReuseAnUnrelatedSubmission() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FunctionalExecutionViewProvider provider = mock(FunctionalExecutionViewProvider.class);
        var source = new FunctionalCaseDetailDTO();
        source.setId("case-1");
        when(provider.current(source)).thenReturn(Optional.empty());
        var response = new TestPlanCaseDetailResponse();
        new PlanCaseEffectiveViewService(jdbc, List.of(provider)).enrich(response, source);
        assertEquals("SOURCE_CASE", response.getExecutionRef().get("type"));
        assertNull(response.getExecutionFacts());
    }
}
