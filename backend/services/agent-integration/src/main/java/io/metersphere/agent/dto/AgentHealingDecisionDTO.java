package io.metersphere.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AgentHealingDecisionDTO {
    private boolean allowed;
    private String reason;
}
