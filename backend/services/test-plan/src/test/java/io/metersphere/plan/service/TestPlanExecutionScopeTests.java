package io.metersphere.plan.service;

import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.domain.TestPlanFunctionalCase;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestPlanExecutionScopeTests {
    private final TestPlanFunctionalCaseService service = new TestPlanFunctionalCaseService();
    private final TestPlanMapper plans = mock(TestPlanMapper.class);
    private final FunctionalCaseMapper cases = mock(FunctionalCaseMapper.class);
    private final TestPlan plan = new TestPlan();
    private final TestPlanFunctionalCase relation = new TestPlanFunctionalCase();

    TestPlanExecutionScopeTests() {
        ReflectionTestUtils.setField(service, "testPlanMapper", plans);
        ReflectionTestUtils.setField(service, "functionalCaseMapper", cases);
        plan.setId("plan-a"); plan.setProjectId("project-a"); plan.setStatus("UNDERWAY");
        relation.setTestPlanId("plan-a"); relation.setFunctionalCaseId("case-a");
        when(plans.selectByPrimaryKey("plan-a")).thenReturn(plan);
    }

    private void validate(String project) {
        ReflectionTestUtils.invokeMethod(service, "validateExecutionScope", List.of(relation), "plan-a", project);
    }

    @Test void wrongProjectAndForeignPlanAreRejectedBeforeReadingCases() {
        assertThrows(MSException.class, () -> validate("other-project"));
        relation.setTestPlanId("other-plan");
        assertThrows(MSException.class, () -> validate("project-a"));
        verifyNoInteractions(cases);
    }

    @Test void deletedOrForeignSourceCaseCannotBeExecuted() {
        when(cases.selectByExample(any())).thenReturn(List.of());
        assertThrows(MSException.class, () -> validate("project-a"));
        verify(cases).selectByExample(argThat(example -> {
            var conditions = example.getOredCriteria().getFirst().getAllCriteria();
            return conditions.stream().anyMatch(c -> "project_id =".equals(c.getCondition()) && "project-a".equals(c.getValue()))
                    && conditions.stream().anyMatch(c -> "deleted =".equals(c.getCondition()) && Boolean.FALSE.equals(c.getValue()));
        }));
    }

    @Test void archivedPlanIsRejectedBeforeSourceMutation() {
        plan.setStatus("ARCHIVED");
        try (var text = mockStatic(Translator.class)) {
            text.when(() -> Translator.get("test_plan.is.archived")).thenReturn("archived");
            assertEquals("archived", assertThrows(MSException.class, () -> validate("project-a")).getMessage());
        }
        verifyNoInteractions(cases);
    }

    @Test void matchingActivePlanAndSourceAreAccepted() {
        var source = new FunctionalCase(); source.setId("case-a"); source.setProjectId("project-a");
        when(cases.selectByExample(any())).thenReturn(List.of(source));
        assertDoesNotThrow(() -> validate("project-a"));
    }
}
