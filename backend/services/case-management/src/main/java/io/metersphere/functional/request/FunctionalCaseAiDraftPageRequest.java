package io.metersphere.functional.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FunctionalCaseAiDraftPageRequest {
    @NotBlank
    @Schema(description = "Project ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String projectId;

    @Schema(description = "Draft status")
    private String draftStatus;

    @Schema(description = "Current page")
    private Integer current = 1;

    @Schema(description = "Page size")
    private Integer pageSize = 20;
}
