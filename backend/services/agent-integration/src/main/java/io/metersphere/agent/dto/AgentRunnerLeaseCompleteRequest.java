package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentRunnerLeaseCompleteRequest {
    @NotBlank
    private String outcome;
    private String reason;
}
