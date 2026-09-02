package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.mapper.TestAssetMapper;
import io.metersphere.functional.domain.AiSourceDocument;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseAiDraft;
import io.metersphere.functional.event.TestAssetCasePublishedEvent;
import io.metersphere.functional.event.TestAssetCaseCopiedEvent;
import io.metersphere.functional.event.TestAssetDocumentPublishedEvent;
import io.metersphere.functional.event.TestAssetFunctionalCaseChangedEvent;
import io.metersphere.functional.mapper.AiSourceDocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAssetPublicationListenerTests {
    @Mock
    private TestAssetVersionService versionService;
    @Mock
    private TestAssetMapper testAssetMapper;
    @Mock
    private AiSourceDocumentMapper documentMapper;
    @Mock
    private TestAssetGovernanceService governanceService;
    @InjectMocks
    private TestAssetPublicationListener listener;

    @Test
    void documentEventShouldPublishImmutableVersion() {
        AiSourceDocument document = document("project-1", "document-1");

        listener.onDocumentPublished(new TestAssetDocumentPublishedEvent(document, "snapshot"));

        verify(versionService).publish("project-1", "DOCUMENT", "document-1", "sha-1", "snapshot", "user-1");
        verify(governanceService).recordTrustedSource("project-1", "DOCUMENT", "document-1", "IMPORT",
                "SOURCE_DOCUMENT", "document-1", "USER", "user-1");
    }

    @Test
    void normalCaseChangeShouldPublishUsingStableIdentity() {
        FunctionalCase functionalCase = new FunctionalCase();
        functionalCase.setId("case-row-2");
        functionalCase.setRefId("case-stable-2");
        functionalCase.setProjectId("project-1");
        functionalCase.setVersionId("business-version-2");

        listener.onFunctionalCaseChanged(new TestAssetFunctionalCaseChangedEvent(
                functionalCase, "normal-case-snapshot", "editor-1", "MANUAL"));

        verify(versionService).publish("project-1", "CASE", "case-stable-2", "business-version-2",
                "normal-case-snapshot", "editor-1");
        verify(governanceService).recordTrustedSource("project-1", "CASE", "case-stable-2", "MANUAL",
                "FUNCTIONAL_CASE", "case-row-2", "USER", "editor-1");
    }

    @Test
    void caseEventShouldUseStableRefIdAndRelateSourceDocument() {
        FunctionalCase functionalCase = new FunctionalCase();
        functionalCase.setId("case-version-row-1");
        functionalCase.setRefId("case-stable-1");
        functionalCase.setProjectId("project-1");
        functionalCase.setVersionId("business-version-1");
        FunctionalCaseAiDraft draft = new FunctionalCaseAiDraft();
        draft.setId("draft-1");
        draft.setGenerationId("generation-1");
        draft.setProjectId("project-1");
        draft.setSourceDocumentId("document-1");
        draft.setSourceReferences("[]");
        AiSourceDocument document = document("project-1", "document-1");
        TestAssetVersionDTO caseVersion = version("case-asset-version-1");
        TestAssetVersionDTO documentVersion = version("document-asset-version-1");
        when(versionService.publish("project-1", "CASE", "case-stable-1", "business-version-1", "case-snapshot", "reviewer-1"))
                .thenReturn(caseVersion);
        when(documentMapper.selectByPrimaryKey("document-1")).thenReturn(document);
        when(testAssetMapper.selectLatest("project-1", "DOCUMENT", "document-1")).thenReturn(documentVersion);

        listener.onCasePublished(new TestAssetCasePublishedEvent(
                draft, functionalCase, "case-snapshot", "reviewer-1"));

        verify(versionService).relate(eq("project-1"), eq("DERIVED_FROM"), eq("DOCUMENT"), eq("document-1"),
                eq("document-asset-version-1"), eq("CASE"), eq("case-stable-1"), eq("case-asset-version-1"),
                anyString(), eq("reviewer-1"));
    }

    @Test
    void crossProjectSourceDocumentShouldNeverCreateRelation() {
        FunctionalCase functionalCase = new FunctionalCase();
        functionalCase.setId("case-row-1");
        functionalCase.setRefId("case-stable-1");
        functionalCase.setProjectId("project-1");
        functionalCase.setVersionId("version-1");
        FunctionalCaseAiDraft draft = new FunctionalCaseAiDraft();
        draft.setId("draft-1");
        draft.setProjectId("project-1");
        draft.setSourceDocumentId("document-2");
        draft.setSourceReferences("[]");
        when(versionService.publish(eq("project-1"), eq("CASE"), eq("case-stable-1"), eq("version-1"),
                eq("snapshot"), eq("reviewer-1"))).thenReturn(version("case-version-1"));
        when(documentMapper.selectByPrimaryKey("document-2")).thenReturn(document("project-2", "document-2"));

        listener.onCasePublished(new TestAssetCasePublishedEvent(draft, functionalCase, "snapshot", "reviewer-1"));

        verify(versionService, never()).relate(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void copiedCaseShouldMatchCategoryByPathInsteadOfReusingCategoryId() {
        listener.onCaseCopied(new TestAssetCaseCopiedEvent(
                "source-project", "source-case", "target-project", "target-case", "user-1"));

        verify(governanceService).copyCategoryByPath(
                "source-project", "CASE", "source-case", "target-project", "CASE", "target-case", "user-1");
    }

    private AiSourceDocument document(String projectId, String id) {
        AiSourceDocument document = new AiSourceDocument();
        document.setId(id);
        document.setProjectId(projectId);
        document.setSha256("sha-1");
        document.setCreateUser("user-1");
        document.setDeleted(false);
        return document;
    }

    private TestAssetVersionDTO version(String id) {
        TestAssetVersionDTO version = new TestAssetVersionDTO();
        version.setId(id);
        return version;
    }
}
