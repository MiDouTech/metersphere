package io.metersphere.plan.service;

import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.dto.request.ResourceSortRequest;
import io.metersphere.plan.mapper.TestPlanCollectionMapper;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestPlanSortScopeTests {
    private final TestPlanResourceService service = mock(TestPlanResourceService.class, CALLS_REAL_METHODS);
    private final TestPlanMapper plans = mock(TestPlanMapper.class);
    private final TestPlanCollectionMapper collections = mock(TestPlanCollectionMapper.class);
    private final ResourceSortRequest request = new ResourceSortRequest();

    TestPlanSortScopeTests() {
        ReflectionTestUtils.setField(service, "testPlanMapper", plans);
        ReflectionTestUtils.setField(service, "testPlanCollectionMapper", collections);
        request.setProjectId("project-a");
        request.setTestCollectionId("collection-a");
        TestPlan plan = new TestPlan();
        plan.setId("plan-a");
        plan.setProjectId("project-a");
        when(plans.selectByPrimaryKey("plan-a")).thenReturn(plan);
        when(collections.countByExample(any())).thenReturn(1L);
    }

    @Test
    void matchingProjectPlanAndCollectionCanSort() {
        assertDoesNotThrow(() -> service.checkSortScope(request, "plan-a", "collection-a", "plan-a", "collection-a", "API"));
        verify(collections).countByExample(argThat(example -> {
            var values = example.getOredCriteria().getFirst().getAllCriteria();
            return values.stream().anyMatch(c -> "test_plan_id =".equals(c.getCondition()) && "plan-a".equals(c.getValue()))
                    && values.stream().anyMatch(c -> "`type` =".equals(c.getCondition()) && "API".equals(c.getValue()));
        }));
    }

    @Test
    void collectionCannotAuthorizeNodesFromAnotherCollectionOrPlan() {
        reject("plan-a", "collection-b", "plan-a", "collection-b");
        reject("plan-a", "collection-a", "plan-b", "collection-a");
        reject("plan-a", "collection-a", "plan-a", "collection-b");
        reject("plan-a", "collection-a", null, null);
        verifyNoInteractions(plans, collections);
    }

    @Test
    void forgedProjectOrDeletedPlanIsRejected() {
        request.setProjectId("project-b");
        reject("plan-a", "collection-a", "plan-a", "collection-a");
        request.setProjectId("project-a");
        reject("missing-plan", "collection-a", "missing-plan", "collection-a");
        verifyNoInteractions(collections);
    }

    @Test
    void missingOrWrongTypeCollectionIsRejected() {
        when(collections.countByExample(any())).thenReturn(0L);
        reject("plan-a", "collection-a", "plan-a", "collection-a");
    }

    private void reject(String plan, String collection, String targetPlan, String targetCollection) {
        try (var translator = mockStatic(Translator.class)) {
            translator.when(() -> Translator.get(anyString())).thenAnswer(call -> call.getArgument(0));
            assertThrows(MSException.class,
                    () -> service.checkSortScope(request, plan, collection, targetPlan, targetCollection, "API"));
        }
    }
}
