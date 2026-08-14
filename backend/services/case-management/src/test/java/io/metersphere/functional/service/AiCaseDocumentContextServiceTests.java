package io.metersphere.functional.service;

import io.metersphere.functional.constants.AiSourceDocumentParseStatus;
import io.metersphere.functional.domain.AiSourceDocument;
import io.metersphere.functional.mapper.AiSourceDocumentMapper;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiCaseDocumentContextServiceTests {

    @Test
    void resolvesOnlyRequestedProjectDocumentsInStableOrder() {
        AiSourceDocumentMapper mapper = mock(AiSourceDocumentMapper.class);
        AiSourceDocument second = document("doc-2", "rules.md", "Rule B");
        AiSourceDocument first = document("doc-1", "story.md", "Rule A");
        when(mapper.selectByIdsInProject(List.of("doc-1", "doc-2"), "project-1"))
                .thenReturn(List.of(second, first));

        AiCaseDocumentContextService.ResolvedContext context =
                new AiCaseDocumentContextService(mapper).resolve("project-1", List.of(" doc-1 ", "doc-2", "doc-1"));

        assertEquals(List.of("doc-1", "doc-2"), context.documentIds());
        assertTrue(context.promptContext().indexOf("doc-1") < context.promptContext().indexOf("doc-2"));
        assertTrue(context.promptContext().contains("不可信业务资料"));
    }

    @Test
    void rejectsMissingCrossProjectOrDeletedDocument() {
        AiSourceDocumentMapper mapper = mock(AiSourceDocumentMapper.class);
        when(mapper.selectByIdsInProject(List.of("foreign-doc"), "project-1")).thenReturn(List.of());
        assertThrows(MSException.class,
                () -> new AiCaseDocumentContextService(mapper).resolve("project-1", List.of("foreign-doc")));
    }

    @Test
    void rejectsDocumentThatIsNotParsed() {
        AiSourceDocumentMapper mapper = mock(AiSourceDocumentMapper.class);
        AiSourceDocument document = document("doc-1", "pending.md", "");
        document.setParseStatus(AiSourceDocumentParseStatus.PARSING.name());
        when(mapper.selectByIdsInProject(List.of("doc-1"), "project-1")).thenReturn(List.of(document));
        assertThrows(MSException.class,
                () -> new AiCaseDocumentContextService(mapper).resolve("project-1", List.of("doc-1")));
    }

    private AiSourceDocument document(String id, String name, String summary) {
        AiSourceDocument document = new AiSourceDocument();
        document.setId(id);
        document.setOriginalName(name);
        document.setSummary(summary);
        document.setSectionIndex("section");
        document.setParseStatus(AiSourceDocumentParseStatus.PARSED.name());
        return document;
    }
}
