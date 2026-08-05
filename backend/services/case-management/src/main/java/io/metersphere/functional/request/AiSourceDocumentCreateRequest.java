package io.metersphere.functional.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiSourceDocumentCreateRequest {
    @NotBlank
    @Schema(description = "Project ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String projectId;

    @Schema(description = "AI conversation ID")
    private String conversationId;

    @NotBlank
    @Schema(description = "File service ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileId;

    @NotBlank
    @Schema(description = "Original file name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String originalName;

    @Schema(description = "MIME type")
    private String mimeType;

    @Schema(description = "File size")
    private Long fileSize;

    @Schema(description = "SHA-256 digest")
    private String sha256;
}
