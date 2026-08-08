package io.metersphere.functional.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiCaseConversationModelRequest {
    @NotBlank
    private String projectId;
    @NotBlank
    private String conversationId;
    @NotBlank
    private String modelSourceId;
}
