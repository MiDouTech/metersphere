package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentExecutionTaskSearchRequest {
    private String projectId;
    private String keyword;
    private String status;
    private String verdict;
    private String taskOrigin;
    private String executorChannel;
    /** @deprecated compatibility filter; use executorChannel. */
    @Deprecated
    private String executionMode;
    private Integer current = 1;
    private Integer pageSize = 20;
}
