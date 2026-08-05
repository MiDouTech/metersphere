package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentExecutionEventsRequest {
    private Long cursor = 0L;
    private Integer limit = 100;
}
