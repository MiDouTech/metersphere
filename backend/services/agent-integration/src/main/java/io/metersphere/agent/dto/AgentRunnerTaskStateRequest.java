package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentRunnerTaskStateRequest {
    @NotBlank
    private String status;
    private String reason;
}
