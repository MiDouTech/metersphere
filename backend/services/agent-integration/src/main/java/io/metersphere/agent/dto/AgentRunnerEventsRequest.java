package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AgentRunnerEventsRequest {
    @NotBlank
    private String leaseId;
    @NotEmpty
    private List<AgentExecutionEventDTO> events;
}
