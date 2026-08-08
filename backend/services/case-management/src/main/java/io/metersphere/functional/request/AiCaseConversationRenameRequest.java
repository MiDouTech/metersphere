package io.metersphere.functional.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiCaseConversationRenameRequest {
    @NotBlank
    private String projectId;
    @NotBlank
    private String conversationId;
    @NotBlank
    @Size(max = 255)
    private String title;
}
