package io.metersphere.system.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiProviderChatRequest {
    @NotBlank
    private String projectId;
    @NotBlank
    private String chatModelId;
    @NotBlank
    private String prompt;
    private String system;
    private String conversationId;
    private String requestId;
    private String organizationId;
}
