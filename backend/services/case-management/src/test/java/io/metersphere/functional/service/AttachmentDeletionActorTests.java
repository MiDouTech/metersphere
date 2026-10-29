package io.metersphere.functional.service;

import io.metersphere.functional.domain.FunctionalCaseAttachment;
import io.metersphere.functional.mapper.FunctionalCaseAttachmentMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.system.service.FileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttachmentDeletionActorTests {
    private final FunctionalCaseAttachmentService service = new FunctionalCaseAttachmentService();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FunctionalCaseAttachmentMapper rows = mock(FunctionalCaseAttachmentMapper.class);
    private final FileService files = mock(FileService.class);
    private final FunctionalImportEffectOutbox effects = mock(FunctionalImportEffectOutbox.class);

    AttachmentDeletionActorTests() {
        ReflectionTestUtils.setField(service, "syncJdbc", jdbc);
        ReflectionTestUtils.setField(service, "functionalCaseAttachmentMapper", rows);
        ReflectionTestUtils.setField(service, "fileService", files);
        ReflectionTestUtils.setField(service, "importEffects", effects);
        ReflectionTestUtils.setField(service, "evidenceStore", mock(FunctionalCaseEvidenceStore.class));
        when(jdbc.queryForList("SELECT project_id FROM functional_case WHERE id=?", String.class, "case-a"))
                .thenReturn(List.of("project-a"));
    }

    @Test
    void missingActorAndWrongProjectFailBeforeAttachmentOrStorageWrites() {
        assertThrows(MSException.class, () -> service.deleteCaseAttachment(List.of("file"), "case-a", "project-a", null));
        assertThrows(MSException.class, () -> service.deleteCaseAttachment(List.of("file"), "case-a", "other-project", "agent-user"));
        verifyNoInteractions(rows, files, effects);
    }

    @Test
    void deletingAnAlreadyAbsentAttachmentDoesNotBumpVersionOrEnqueueAgain() {
        when(rows.selectByExample(any())).thenReturn(List.of());
        service.deleteCaseAttachment(List.of("file"), "case-a", "project-a", "agent-user");
        verify(rows, never()).deleteByExample(any());
        verifyNoInteractions(files, effects);
        verify(jdbc, never()).update(anyString(), anyLong(), anyString());
    }

    @Test
    void storageUsesProjectWhileMirrorUsesExplicitActor() throws Exception {
        var row = new FunctionalCaseAttachment();
        row.setCaseId("case-a"); row.setFileId("file"); row.setFileName("file.txt"); row.setLocal(true);
        when(rows.selectByExample(any())).thenReturn(List.of(row));
        service.deleteCaseAttachment(List.of("file"), "case-a", "project-a", "agent-user");
        var request = ArgumentCaptor.forClass(FileRequest.class);
        verify(files).deleteFile(request.capture());
        assertTrue(request.getValue().getFolder().contains("project-a"));
        assertFalse(request.getValue().getFolder().contains("agent-user"));
        verify(effects).enqueue("project-a", "agent-user", List.of("case-a"), List.of(), "UPDATE");
    }
}
