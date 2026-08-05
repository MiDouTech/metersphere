package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentTestPlanSearchRequest {
    private String projectId;
    private String keyword;
    private String status;
    private Boolean includeArchived = false;
    private Integer page = 1;
    private Integer pageSize = 20;
}
