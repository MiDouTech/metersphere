package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.mapper.TestAssetMapper;
import io.metersphere.functional.domain.AiSourceDocument;
import io.metersphere.functional.dto.CaseGenerationSourceRefDTO;
import io.metersphere.functional.event.TestAssetCasePublishedEvent;
import io.metersphere.functional.event.TestAssetDocumentPublishedEvent;
import io.metersphere.functional.event.TestAssetFunctionalCaseChangedEvent;
import io.metersphere.functional.event.TestAssetCaseCopiedEvent;
import io.metersphere.functional.mapper.AiSourceDocumentMapper;
import io.metersphere.bug.event.TestAssetBugCreatedEvent;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TestAssetPublicationListener {
    @Resource
    private TestAssetVersionService versionService;
    @Resource
    private TestAssetMapper testAssetMapper;
    @Resource
    private AiSourceDocumentMapper documentMapper;
    @Resource
    private TestAssetGovernanceService governanceService;

    @EventListener
    public void onDocumentPublished(TestAssetDocumentPublishedEvent event) {
        AiSourceDocument document = event.document();
        if (document == null || StringUtils.isAnyBlank(document.getProjectId(), document.getId())) {
            return;
        }
        versionService.publish(document.getProjectId(), "DOCUMENT", document.getId(), document.getSha256(),
                StringUtils.defaultIfBlank(event.contentSnapshot(), JSON.toJSONString(document)), document.getCreateUser());
        governanceService.recordTrustedSource(document.getProjectId(), "DOCUMENT", document.getId(), "IMPORT",
                "SOURCE_DOCUMENT", document.getId(), "USER", document.getCreateUser());
    }

    @EventListener
    public void onFunctionalCaseChanged(TestAssetFunctionalCaseChangedEvent event) {
        if (event.functionalCase() == null || StringUtils.isBlank(event.functionalCase().getProjectId())) {
            return;
        }
        String stableCaseId = StringUtils.defaultIfBlank(
                event.functionalCase().getRefId(), event.functionalCase().getId());
        versionService.publish(event.functionalCase().getProjectId(), "CASE", stableCaseId,
                event.functionalCase().getVersionId(), event.contentSnapshot(), event.userId());
        if (StringUtils.isNotBlank(event.creationSource())) {
            governanceService.recordTrustedSource(event.functionalCase().getProjectId(), "CASE", stableCaseId,
                    event.creationSource(), "FUNCTIONAL_CASE", event.functionalCase().getId(), "USER", event.userId());
        }
    }

    @EventListener
    public void onCasePublished(TestAssetCasePublishedEvent event) {
        if (event.functionalCase() == null || event.draft() == null) {
            return;
        }
        String projectId = event.functionalCase().getProjectId();
        String stableCaseId = StringUtils.defaultIfBlank(event.functionalCase().getRefId(), event.functionalCase().getId());
        TestAssetVersionDTO caseVersion = versionService.publish(projectId, "CASE", stableCaseId,
                event.functionalCase().getVersionId(), event.contentSnapshot(), event.userId());
        governanceService.recordTrustedSource(projectId, "CASE", stableCaseId, "AI",
                "AI_GENERATION", event.draft().getGenerationId(), "USER", event.userId());

        Map<String, List<CaseGenerationSourceRefDTO>> references = sourceReferences(event.draft().getSourceReferences());
        if (StringUtils.isNotBlank(event.draft().getSourceDocumentId())) {
            references.computeIfAbsent(event.draft().getSourceDocumentId(), ignored -> new ArrayList<>());
        }
        references.forEach((documentId, documentReferences) -> relateDocument(
                projectId, documentId, documentReferences, stableCaseId, caseVersion, event));
    }

    @EventListener
    public void onBugCreated(TestAssetBugCreatedEvent event) {
        if (event.bug() == null || StringUtils.isAnyBlank(event.bug().getProjectId(), event.bug().getId())) return;
        governanceService.recordTrustedSource(event.bug().getProjectId(), "BUG", event.bug().getId(),
                event.creationSource(), "BUG_CREATE", event.sourceReferenceId(),
                "AI".equals(event.creationSource()) ? "AGENT" : "USER", event.actorId());
    }

    @EventListener
    public void onCaseCopied(TestAssetCaseCopiedEvent event) {
        governanceService.copyCategoryByPath(event.sourceProjectId(), "CASE", event.sourceAssetId(),
                event.targetProjectId(), "CASE", event.targetAssetId(), event.userId());
    }

    private void relateDocument(String projectId, String documentId, List<CaseGenerationSourceRefDTO> references,
                                String stableCaseId, TestAssetVersionDTO caseVersion, TestAssetCasePublishedEvent event) {
        AiSourceDocument document = documentMapper.selectByPrimaryKey(documentId);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())
                || !StringUtils.equals(projectId, document.getProjectId())) {
            log.warn("Skip invalid AI case source document relation, projectId={}, documentId={}", projectId, documentId);
            return;
        }
        TestAssetVersionDTO documentVersion = testAssetMapper.selectLatest(projectId, "DOCUMENT", documentId);
        if (documentVersion == null) {
            documentVersion = versionService.publish(projectId, "DOCUMENT", documentId, document.getSha256(),
                    JSON.toJSONString(document), document.getCreateUser());
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("generationId", event.draft().getGenerationId());
        metadata.put("draftId", event.draft().getId());
        metadata.put("references", references);
        versionService.relate(projectId, "DERIVED_FROM", "DOCUMENT", documentId, documentVersion.getId(),
                "CASE", stableCaseId, caseVersion.getId(), JSON.toJSONString(metadata), event.userId());
    }

    private Map<String, List<CaseGenerationSourceRefDTO>> sourceReferences(String sourceReferences) {
        Map<String, List<CaseGenerationSourceRefDTO>> grouped = new LinkedHashMap<>();
        if (StringUtils.isBlank(sourceReferences)) {
            return grouped;
        }
        try {
            List<CaseGenerationSourceRefDTO> references = JSON.parseArray(sourceReferences, CaseGenerationSourceRefDTO.class);
            if (CollectionUtils.isNotEmpty(references)) {
                references.stream().filter(reference -> StringUtils.isNotBlank(reference.getDocumentId()))
                        .forEach(reference -> grouped.computeIfAbsent(reference.getDocumentId(), ignored -> new ArrayList<>())
                                .add(reference));
            }
        } catch (Exception ex) {
            log.warn("Ignore malformed AI case source references", ex);
        }
        return grouped;
    }
}
