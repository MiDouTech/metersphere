package io.metersphere.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AgentRunnerControlDTO {
    private String taskStatus;
    private String command;
    private Long serverTime;
}
