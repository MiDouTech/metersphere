package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentHumanCreateRequest {
    @NotBlank
    @Size(max = 128)
    private String requestId;
    @NotBlank
    private String requestType;
    @NotBlank
    @Size(max = 255)
    private String title;
    @Size(max = 4000)
    private String content;
    private String riskLevel;
    private Long expiresAt;
}
