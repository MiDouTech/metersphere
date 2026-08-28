package io.metersphere.plan.service;

import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.domain.TestPlanFunctionalCase;
import io.metersphere.plan.mapper.ExtTestPlanCaseExecuteHistoryMapper;
import io.metersphere.plan.mapper.ExtTestPlanCollectionMapper;
import io.metersphere.plan.mapper.ExtTestPlanFunctionalCaseMapper;
import io.metersphere.plan.mapper.TestPlanFunctionalCaseMapper;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.plan.dto.response.TestPlanFunctionalCaseSyncResponse;
import io.metersphere.sdk.constants.ExecStatus;
import io.metersphere.sdk.constants.ResultStatus;
import io.metersphere.sdk.constants.TestPlanConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestPlanFunctionalCaseSyncServiceTests {

    @Mock
    private TestPlanMapper testPlanMapper;
    @Mock
    private TestPlanFunctionalCaseMapper relationMapper;
    @Mock
    private ExtTestPlanFunctionalCaseMapper extRelationMapper;
    @Mock
    private ExtTestPlanCollectionMapper collectionMapper;
    @Mock
    private FunctionalCaseMapper functionalCaseMapper;
    @Mock
    private ExtTestPlanCaseExecuteHistoryMapper historyMapper;
    @Mock
    private MessageSource messageSource;

    private TestPlanFunctionalCaseService service;

    @BeforeEach
    void setUp() {
        new Translator().setMessageSource(messageSource);
        service = new TestPlanFunctionalCaseService();
        ReflectionTestUtils.setField(service, "testPlanMapper", testPlanMapper);
        ReflectionTestUtils.setField(service, "testPlanFunctionalCaseMapper", relationMapper);
        ReflectionTestUtils.setField(service, "extTestPlanFunctionalCaseMapper", extRelationMapper);
        ReflectionTestUtils.setField(service, "extTestPlanCollectionMapper", collectionMapper);
        ReflectionTestUtils.setField(service, "functionalCaseMapper", functionalCaseMapper);
        ReflectionTestUtils.setField(service, "extTestPlanCaseExecuteHistoryMapper", historyMapper);
    }

    @Test
    void syncPlanUpdatesAndRemovesAssociatedCasesWithoutAddingNewCases() {
        TestPlan plan = plan(TestPlanConstants.TEST_PLAN_STATUS_NOT_ARCHIVED);
        when(testPlanMapper.selectByPrimaryKey("plan-1")).thenReturn(plan);
        when(extRelationMapper.lockTestPlan("plan-1")).thenReturn("plan-1");
        when(collectionMapper.selectDefaultCollectionId("plan-1", "FUNCTIONAL")).thenReturn("collection-1");
        when(functionalCaseMapper.selectByExample(any())).thenReturn(List.of(
                functionalCase("new", false, true, ResultStatus.SUCCESS.name(), 300L, 100L),
                functionalCase("existing", false, true, ResultStatus.SUCCESS.name(), 200L, 200L),
                functionalCase("deleted", true, true, ExecStatus.PENDING.name(), null, 300L),
                functionalCase("old-version", false, false, ExecStatus.PENDING.name(), null, 400L)
        ));
        when(relationMapper.selectByExample(any())).thenReturn(List.of(
                relation("relation-existing", "existing", ExecStatus.PENDING.name(), 100L),
                relation("relation-deleted", "deleted", ExecStatus.PENDING.name(), null),
                relation("relation-old", "old-version", ExecStatus.PENDING.name(), null),
                relation("relation-other-project", "other-project-case", ExecStatus.PENDING.name(), null)
        ));
        when(extRelationMapper.updateSyncStatus("relation-existing", ResultStatus.SUCCESS.name(), 200L)).thenReturn(1);

        TestPlanFunctionalCaseSyncResponse response = service.syncPlanCases("plan-1", "user-1");

        assertEquals(1, response.getPlanCount());
        assertEquals(0, response.getAddedCount());
        assertEquals(1, response.getUpdatedCount());
        assertEquals(2, response.getRemovedCount());
        verify(relationMapper).deleteByExample(any());
        verify(historyMapper).updateDeleted(anyList(), org.mockito.ArgumentMatchers.eq(true));
        verify(relationMapper, never()).batchInsert(anyList());
    }

    @Test
    void syncPlanIsIdempotentWhenSourceAndPlanAlreadyMatch() {
        TestPlan plan = plan(TestPlanConstants.TEST_PLAN_STATUS_NOT_ARCHIVED);
        when(testPlanMapper.selectByPrimaryKey("plan-1")).thenReturn(plan);
        when(extRelationMapper.lockTestPlan("plan-1")).thenReturn("plan-1");
        when(collectionMapper.selectDefaultCollectionId("plan-1", "FUNCTIONAL")).thenReturn("collection-1");
        when(functionalCaseMapper.selectByExample(any())).thenReturn(List.of(
                functionalCase("existing", false, true, ResultStatus.SUCCESS.name(), 200L, 100L)
        ));
        when(relationMapper.selectByExample(any())).thenReturn(List.of(
                relation("relation-existing", "existing", ResultStatus.SUCCESS.name(), 200L)
        ));

        TestPlanFunctionalCaseSyncResponse response = service.syncPlanCases("plan-1", "user-1");

        assertEquals(1, response.getPlanCount());
        assertEquals(0, response.getAddedCount());
        assertEquals(0, response.getUpdatedCount());
        assertEquals(0, response.getRemovedCount());
        verify(relationMapper, never()).batchInsert(anyList());
        verify(relationMapper, never()).deleteByExample(any());
        verify(extRelationMapper, never()).updateSyncStatus(any(), any(), any());
    }

    @Test
    void archivedPlanCannotBeSynchronized() {
        TestPlan plan = plan(TestPlanConstants.TEST_PLAN_STATUS_ARCHIVED);
        when(testPlanMapper.selectByPrimaryKey("plan-1")).thenReturn(plan);
        when(extRelationMapper.lockTestPlan("plan-1")).thenReturn("plan-1");

        assertThrows(MSException.class, () -> service.syncPlanCases("plan-1", "user-1"));
        verify(functionalCaseMapper, never()).selectByExample(any());
    }

    private static TestPlan plan(String status) {
        TestPlan plan = new TestPlan();
        plan.setId("plan-1");
        plan.setProjectId("project-1");
        plan.setType(TestPlanConstants.TEST_PLAN_TYPE_PLAN);
        plan.setStatus(status);
        return plan;
    }

    private static FunctionalCase functionalCase(String id, boolean deleted, boolean latest, String result,
                                                 Long executeTime, Long pos) {
        FunctionalCase functionalCase = new FunctionalCase();
        functionalCase.setId(id);
        functionalCase.setProjectId("project-1");
        functionalCase.setDeleted(deleted);
        functionalCase.setLatest(latest);
        functionalCase.setLastExecuteResult(result);
        functionalCase.setLastExecuteTime(executeTime);
        functionalCase.setPos(pos);
        return functionalCase;
    }

    private static TestPlanFunctionalCase relation(String id, String caseId, String result, Long executeTime) {
        TestPlanFunctionalCase relation = new TestPlanFunctionalCase();
        relation.setId(id);
        relation.setTestPlanId("plan-1");
        relation.setFunctionalCaseId(caseId);
        relation.setLastExecResult(result);
        relation.setLastExecTime(executeTime);
        return relation;
    }
}
