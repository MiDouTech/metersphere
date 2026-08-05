package io.metersphere.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentExecutionEventsResponse {
    private Long cursor;
    private Boolean hasMore;
    private List<AgentExecutionEventDTO> events = new ArrayList<>();
}
