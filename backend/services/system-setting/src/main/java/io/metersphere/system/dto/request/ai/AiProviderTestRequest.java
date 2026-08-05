package io.metersphere.system.dto.request.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiProviderTestRequest {
    @NotBlank
    @Schema(description = "AI model source ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String chatModelId;

    @Schema(description = "Test prompt")
    private String prompt = "请回复 OK";

    @Schema(description = "Conversation ID")
    private String conversationId;

    @Schema(description = "Organization ID")
    private String organizationId;
}
