package io.metersphere.functional.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiCaseAgentChatRequest {
    @NotBlank
    private String projectId;
    @NotBlank
    private String conversationId;
    @Size(max = 64)
    private String requestId;
    @NotBlank
    @Size(max = 20000)
    private String message;
}
