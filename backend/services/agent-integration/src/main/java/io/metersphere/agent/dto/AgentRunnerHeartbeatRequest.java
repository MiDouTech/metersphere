package io.metersphere.agent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentRunnerHeartbeatRequest {
    @NotBlank
    private String runnerId;
    @Min(0)
    private Integer activeCount = 0;
}
