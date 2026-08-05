package io.metersphere.functional.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FunctionalCaseAiGenerationCancelRequest {
    @NotBlank
    @Schema(description = "Project ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String projectId;

    @NotBlank
    @Schema(description = "Generation task ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String generationId;
}
