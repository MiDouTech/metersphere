package io.metersphere.functional.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FunctionalCaseAiDraftUpsertRequest {
    @Schema(description = "Draft ID; empty when creating")
    private String id;

    @NotBlank
    @Schema(description = "Generation task ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String generationId;

    @Schema(description = "Source document ID")
    private String sourceDocumentId;

    @NotBlank
    @Schema(description = "Project ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String projectId;

    @Schema(description = "Module ID")
    private String moduleId;

    @Schema(description = "Template ID")
    private String templateId;

    @NotBlank
    @Schema(description = "Case name", requiredMode = Schema.RequiredMode.REQUIRED)
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

    @Schema(description = "Validation message")
    private String validationMessage;

    @Schema(description = "Validation status")
    private String validationStatus;

    @Schema(description = "Draft status")
    private String draftStatus;

    @Schema(description = "Optimistic lock version")
    private Integer version;
}
