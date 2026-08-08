package io.metersphere.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentWebStepPlanDTO {
    private AgentWebActionDTO action;
    private List<AgentWebAssertionDTO> assertions;
}
