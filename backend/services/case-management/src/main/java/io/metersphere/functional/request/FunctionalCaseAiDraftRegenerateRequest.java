package io.metersphere.functional.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FunctionalCaseAiDraftRegenerateRequest {
    @NotBlank
    @Schema(description = "Project ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String projectId;

    @NotBlank
    @Schema(description = "Draft ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String draftId;

    @NotBlank
    @Schema(description = "AI model ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String chatModelId;

    @NotBlank
    @Schema(description = "AI conversation ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conversationId;

    @NotBlank
    @Schema(description = "Organization ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String organizationId;

    @Schema(description = "Extra prompt")
    private String prompt;
}
