package io.metersphere.functional.service;

import io.metersphere.functional.domain.CaseReview;
import io.metersphere.functional.mapper.ExtCaseReviewMapper;
import io.metersphere.functional.request.CaseReviewRequest;
import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaseReviewTestPlanTests {
    @Mock private TestPlanMapper testPlanMapper;
    @Mock private ExtCaseReviewMapper extCaseReviewMapper;
    @InjectMocks private CaseReviewService service;

    private CaseReview review() {
        CaseReview review = new CaseReview();
        review.setId("review");
        review.setProjectId("project");
        review.setTestPlanId("old-plan");
        return review;
    }

    @Test
    void omittedFieldPreservesExistingAssociation() {
        CaseReview review = review();
        service.setTestPlan(new CaseReviewRequest(), review);
        assertEquals("old-plan", review.getTestPlanId());
        verifyNoInteractions(testPlanMapper, extCaseReviewMapper);
    }

    @Test
    void emptyFieldClearsAssociation() {
        CaseReviewRequest request = new CaseReviewRequest();
        request.setTestPlanId("");
        CaseReview review = review();
        service.setTestPlan(request, review);
        assertNull(review.getTestPlanId());
        verify(extCaseReviewMapper).setTestPlan("review", null);
        verifyNoInteractions(testPlanMapper);
    }

    @Test
    void associatesPlanInSameProject() {
        TestPlan plan = new TestPlan();
        plan.setProjectId("project");
        plan.setType("TEST_PLAN");
        when(testPlanMapper.selectByPrimaryKey("plan")).thenReturn(plan);
        CaseReviewRequest request = new CaseReviewRequest();
        request.setTestPlanId("plan");
        CaseReview review = review();
        service.setTestPlan(request, review);
        assertEquals("plan", review.getTestPlanId());
        verify(extCaseReviewMapper).setTestPlan("review", "plan");
    }

    @Test
    void rejectsMissingCrossProjectAndGroupPlans() {
        CaseReviewRequest request = new CaseReviewRequest();
        request.setTestPlanId("plan");
        TestPlan crossProject = new TestPlan();
        crossProject.setProjectId("other-project");
        crossProject.setType("TEST_PLAN");
        TestPlan group = new TestPlan();
        group.setProjectId("project");
        group.setType("GROUP");
        when(testPlanMapper.selectByPrimaryKey("plan")).thenReturn(null, crossProject, group);
        try (var translations = mockStatic(Translator.class)) {
            translations.when(() -> Translator.get("case_review.test_plan.invalid")).thenReturn("Invalid plan");
            for (int i = 0; i < 3; i++) {
                assertThrows(MSException.class, () -> service.setTestPlan(request, review()));
            }
        }
        verifyNoInteractions(extCaseReviewMapper);
    }
}
