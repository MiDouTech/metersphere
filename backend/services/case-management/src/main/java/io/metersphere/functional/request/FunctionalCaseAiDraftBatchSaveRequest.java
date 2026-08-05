package io.metersphere.functional.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class FunctionalCaseAiDraftBatchSaveRequest {
    @NotBlank
    @Schema(description = "Project ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String projectId;

    @Schema(description = "Default module ID")
    private String moduleId;

    @Schema(description = "Default template ID")
    private String templateId;

    @NotEmpty
    @Schema(description = "Draft IDs", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> draftIds;
}
