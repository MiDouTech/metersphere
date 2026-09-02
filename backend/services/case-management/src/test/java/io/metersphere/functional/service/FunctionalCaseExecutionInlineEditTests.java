package io.metersphere.functional.service;

import io.metersphere.functional.mapper.ExtFunctionalCaseMapper;
import io.metersphere.functional.request.FunctionalCaseBatchUpdateExecutorRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FunctionalCaseExecutionInlineEditTests {

    @Test
    void clearingExecutorPersistsNullForCaseAndAssociatedPlans() {
        ExtFunctionalCaseMapper mapper = mock(ExtFunctionalCaseMapper.class);
        FunctionalCaseService service = new FunctionalCaseService();
        ReflectionTestUtils.setField(service, "extFunctionalCaseMapper", mapper);

        FunctionalCaseBatchUpdateExecutorRequest request = new FunctionalCaseBatchUpdateExecutorRequest();
        request.setProjectId("project-1");
        request.setSelectAll(false);
        request.setSelectIds(List.of("case-1"));
        request.setUserId("  ");

        service.batchUpdateExecutor(request);

        verify(mapper).batchUpdateExecutor(List.of("case-1"), null);
        verify(mapper).syncPlanExecutorByCaseIds(List.of("case-1"), null);
    }
}
