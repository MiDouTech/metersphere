package io.metersphere.functional.service;

import io.metersphere.functional.domain.AiSourceDocument;
import io.metersphere.functional.mapper.AiSourceDocumentMapper;
import io.metersphere.functional.request.AiSourceDocumentIdRequest;
import io.metersphere.functional.request.AiSourceDocumentPageRequest;
import io.metersphere.functional.response.AiSourceDocumentPageResponse;
import io.metersphere.project.service.FileMetadataService;
import io.metersphere.system.service.ai.AiAuditService;
import io.metersphere.system.service.ai.AiGovernanceService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSourceDocumentServiceTests {
    @Mock
    private AiSourceDocumentMapper mapper;
    @Mock
    private FileMetadataService fileMetadataService;
    @Mock
    private AiSourceDocumentParserService parserService;
    @Mock
    private AiGovernanceService governanceService;
    @Mock
    private AiAuditService auditService;
    @Mock
    private AiDocumentVirusScanner virusScanner;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private AiSourceDocumentService service;

    @Test
    void pageShouldListAllAuthorizedProjectDocumentsInsteadOfOnlyCreatorDocuments() {
        AiSourceDocument document = document("document-1", "project-1", "another-user", "PARSED");
        when(mapper.countByProject("project-1", "PARSED")).thenReturn(1L);
        when(mapper.selectByProject("project-1", "PARSED", 0L, 20L)).thenReturn(List.of(document));
        AiSourceDocumentPageRequest request = new AiSourceDocumentPageRequest();
        request.setProjectId("project-1");
        request.setParseStatus("PARSED");
        request.setCurrent(1);
        request.setPageSize(20);

        AiSourceDocumentPageResponse result = service.page(request, "current-user");

        Assertions.assertEquals(1L, result.getTotal());
        Assertions.assertEquals("another-user", result.getRecords().getFirst().getCreateUser());
        verify(mapper).selectByProject("project-1", "PARSED", 0L, 20L);
    }

    @Test
    void deleteShouldUseProjectScopeForAuthorizedProjectMember() {
        AiSourceDocument document = document("document-1", "project-1", "another-user", "PARSED");
        when(mapper.selectByPrimaryKey("document-1")).thenReturn(document);
        when(mapper.markDeletedInProject(eq("document-1"), eq("project-1"), anyLong())).thenReturn(1);
        AiSourceDocumentIdRequest request = new AiSourceDocumentIdRequest();
        request.setId("document-1");
        request.setProjectId("project-1");

        service.delete(request, "current-user");

        verify(mapper).markDeletedInProject(eq("document-1"), eq("project-1"), anyLong());
        verify(auditService).record(eq("project-1"), any(), eq("current-user"), eq("project-1"),
                eq("DELETE"), eq("AI_SOURCE_DOCUMENT_DELETE"), eq("/functional/case/ai/document"),
                eq("POST"), anyMap());
    }

    private AiSourceDocument document(String id, String projectId, String createUser, String parseStatus) {
        AiSourceDocument document = new AiSourceDocument();
        document.setId(id);
        document.setProjectId(projectId);
        document.setCreateUser(createUser);
        document.setParseStatus(parseStatus);
        document.setDeleted(false);
        return document;
    }
}
