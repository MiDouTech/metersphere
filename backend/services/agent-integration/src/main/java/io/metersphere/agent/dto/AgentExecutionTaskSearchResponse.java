package io.metersphere.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentExecutionTaskSearchResponse {
    private long total;
    private int current;
    private int pageSize;
    private List<AgentExecutionTaskDTO> items = new ArrayList<>();
}
