package io.metersphere.functional.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiSourceDocumentUploadRequest {
    @NotBlank
    @Schema(description = "Project ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String projectId;

    @Schema(description = "AI conversation ID")
    private String conversationId;
}
