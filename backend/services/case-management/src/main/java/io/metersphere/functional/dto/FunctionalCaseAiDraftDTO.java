package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FunctionalCaseAiDraftDTO {
    @Schema(description = "Draft ID")
    private String id;
    @Schema(description = "Generation task ID")
    private String generationId;
    @Schema(description = "Source document ID")
    private String sourceDocumentId;
    @Schema(description = "Project ID")
    private String projectId;
    @Schema(description = "Module ID")
    private String moduleId;
    @Schema(description = "Template ID")
    private String templateId;
    @Schema(description = "Case name")
    private String name;
    @Schema(description = "Case level")
    private String caseLevel;
    @Schema(description = "Edit type")
    private String editType;
    @Schema(description = "Prerequisite")
    private String prerequisite;
    @Schema(description = "Steps JSON")
    private String steps;
    @Schema(description = "Expected result")
    private String expectedResult;
    @Schema(description = "Tags JSON")
    private String tags;
    @Schema(description = "Custom fields JSON")
    private String customFields;
    @Schema(description = "Source references JSON")
    private String sourceReferences;
    @Schema(description = "Validation message")
    private String validationMessage;
    @Schema(description = "Duplicate fingerprint")
    private String fingerprint;
    @Schema(description = "Whether similar draft exists")
    private Boolean duplicate;
    @Schema(description = "Validation status")
    private String validationStatus;
    @Schema(description = "Draft status")
    private String draftStatus;
    @Schema(description = "Review status")
    private String reviewStatus;
    private String reviewComment;
    private String reviewedBy;
    private Long reviewedAt;
    @Schema(description = "CREATE/UPDATE/DEPRECATE")
    private String publishMode;
    private String targetCaseId;
    private String baselineSnapshot;
    private String contentHash;
    private String reviewedContentHash;
    @Schema(description = "Official functional case ID")
    private String formalCaseId;
    @Schema(description = "Deleted flag")
    private Boolean deleted;
    @Schema(description = "Optimistic lock version")
    private Integer version;
    @Schema(description = "Creator")
    private String createUser;
    @Schema(description = "Created at")
    private Long createTime;
    @Schema(description = "Updated at")
    private Long updateTime;
}
