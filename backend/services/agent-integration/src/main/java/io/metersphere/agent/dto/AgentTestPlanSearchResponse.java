package io.metersphere.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentTestPlanSearchResponse {
    private Integer page;
    private Integer pageSize;
    private Long total;
    private Boolean hasMore;
    private List<AgentTestPlanDTO> items = new ArrayList<>();
}
