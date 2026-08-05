package io.metersphere.functional.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class FunctionalCaseAiGenerateRequest {
    @NotBlank
    @Schema(description = "Project ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String projectId;

    @Schema(description = "Default module ID for generated drafts")
    private String moduleId;

    @Schema(description = "Default template ID for generated drafts")
    private String templateId;

    @NotBlank
    @Schema(description = "Prompt or user input", requiredMode = Schema.RequiredMode.REQUIRED)
    private String prompt;

    @NotBlank
    @Schema(description = "AI model ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String chatModelId;

    @NotBlank
    @Schema(description = "AI conversation ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conversationId;

    @NotBlank
    @Schema(description = "Organization ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String organizationId;

    @Schema(description = "Max generated case count; default 50, max 100")
    private Integer maxCases;

    @Schema(description = "Parsed source document IDs")
    private List<String> sourceDocumentIds;
}
