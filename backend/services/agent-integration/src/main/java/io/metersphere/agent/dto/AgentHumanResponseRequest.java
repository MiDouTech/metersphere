package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentHumanResponseRequest {
    @NotBlank
    private String action;
    private String response;
    private Integer expectedVersion;
}
