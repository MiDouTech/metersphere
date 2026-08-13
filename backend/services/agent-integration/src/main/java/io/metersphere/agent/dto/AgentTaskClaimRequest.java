package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentTaskClaimRequest {
    private String taskId;
    @NotBlank
    private String projectId;
    private String agentType;
    private List<String> capabilities = new ArrayList<>();
}
