package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentLinkedReviewConfirmRequest {
    @NotBlank @Size(max=50) private String projectId;
    @NotBlank @Size(max=50) private String previewId;
    @NotBlank @Size(max=128) private String requestId;
    private boolean acknowledged;
}
