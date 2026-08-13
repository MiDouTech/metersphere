package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetDocumentDTO;
import io.metersphere.agent.dto.TestAssetRelationDTO;
import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.dto.TestAssetContextDocumentDTO;
import io.metersphere.agent.mapper.TestAssetMapper;
import io.metersphere.system.utils.Pager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAssetCatalogServiceTests {
    @Mock
    private TestAssetMapper mapper;
    @Mock
    private AgentProjectService agentProjectService;
    @InjectMocks
    private TestAssetCatalogService service;

    @Test
    void documentsShouldResolveAccessibleProjectAndCapPageSize() {
        when(agentProjectService.resolveProjectId("project-number")).thenReturn("project-1");
        when(mapper.countDocuments("project-1", "PARSED", "login")).thenReturn(1L);
        TestAssetDocumentDTO document = new TestAssetDocumentDTO();
        document.setId("document-1");
        when(mapper.selectDocuments("project-1", "PARSED", "login", 100L, 100))
                .thenReturn(List.of(document));

        Pager<List<TestAssetDocumentDTO>> result = service.documents(
                "project-number", "PARSED", " login ", 2, 500);

        Assertions.assertEquals(1, result.getTotal());
        Assertions.assertEquals(100, result.getPageSize());
        Assertions.assertEquals("document-1", result.getList().getFirst().getId());
        verify(agentProjectService).resolveProjectId("project-number");
    }

    @Test
    void versionsShouldNormalizeAssetTypeAndEmptyFilters() {
        when(agentProjectService.resolveProjectId("project-1")).thenReturn("project-1");
        when(mapper.countVersions("project-1", "CASE", null, null)).thenReturn(0L);

        Pager<List<TestAssetVersionDTO>> result = service.versions(
                "project-1", " case ", " ", " ", null, null);

        Assertions.assertTrue(result.getList().isEmpty());
        Assertions.assertEquals(20, result.getPageSize());
        verify(mapper).countVersions("project-1", "CASE", null, null);
    }

    @Test
    void relationsShouldApplyProjectIsolationBeforeQuery() {
        when(agentProjectService.resolveProjectId("project-alias")).thenReturn("project-2");
        when(mapper.countRelations("project-2", "DOCUMENT", "doc-1", "DERIVED_FROM", null)).thenReturn(1L);
        TestAssetRelationDTO relation = new TestAssetRelationDTO();
        relation.setId("relation-1");
        when(mapper.selectRelations("project-2", "DOCUMENT", "doc-1", "DERIVED_FROM", null, 0L, 20))
                .thenReturn(List.of(relation));

        Pager<List<TestAssetRelationDTO>> result = service.relations(
                "project-alias", "document", "doc-1", "derived_from", null, 1, 20);

        Assertions.assertEquals("relation-1", result.getList().getFirst().getId());
        verify(agentProjectService).resolveProjectId("project-alias");
    }

    @Test
    void documentContextShouldDeduplicateStableCaseIds() {
        TestAssetContextDocumentDTO document = new TestAssetContextDocumentDTO();
        document.setDocumentId("document-1");
        when(mapper.selectDocumentContextForCases("project-1", List.of("case-1", "case-2")))
                .thenReturn(List.of(document));

        List<TestAssetContextDocumentDTO> result = service.documentContextForCases(
                "project-1", List.of("case-1", "case-1", "case-2"));

        Assertions.assertEquals("document-1", result.getFirst().getDocumentId());
        verify(mapper).selectDocumentContextForCases("project-1", List.of("case-1", "case-2"));
    }
}
