package io.metersphere.functional.service;

import io.metersphere.functional.domain.FunctionalCaseAiDraft;
import io.metersphere.functional.mapper.FunctionalCaseAiDraftMapper;
import io.metersphere.functional.request.FunctionalCaseAiDraftBatchSaveRequest;
import io.metersphere.system.log.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FunctionalCaseAiDraftBatchSaveTransactionTests {
    @Test
    void opensAndRollsBackAnIndependentTransactionForEachFailedDraft() {
        FunctionalCaseAiDraftService service = new FunctionalCaseAiDraftService();
        FunctionalCaseAiDraftMapper draftMapper = mock(FunctionalCaseAiDraftMapper.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        when(draftMapper.selectByIds(List.of("draft-1", "draft-2"), "project-1", "user-1"))
                .thenReturn(List.of(savedDraft("draft-1"), savedDraft("draft-2")));
        ReflectionTestUtils.setField(service, "draftMapper", draftMapper);
        ReflectionTestUtils.setField(service, "transactionManager", transactionManager);
        ReflectionTestUtils.setField(service, "operationLogService", mock(OperationLogService.class));
        FunctionalCaseAiDraftBatchSaveRequest request = new FunctionalCaseAiDraftBatchSaveRequest();
        request.setProjectId("project-1");
        request.setDraftIds(List.of("draft-1", "draft-2"));
        request.setConfirmed(true);

        var response = service.batchSave(request, "user-1", "organization-1");

        assertEquals(0, response.getSuccessCount());
        assertEquals(2, response.getFailureCount());
        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager, times(2)).rollback(any());
    }

    private FunctionalCaseAiDraft savedDraft(String id) {
        FunctionalCaseAiDraft draft = new FunctionalCaseAiDraft();
        draft.setId(id);
        draft.setProjectId("project-1");
        draft.setName(id);
        draft.setDraftStatus("SAVED");
        draft.setCreateUser("user-1");
        return draft;
    }
}
