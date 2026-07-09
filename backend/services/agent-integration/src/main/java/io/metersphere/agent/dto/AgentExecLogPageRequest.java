package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentExecLogPageRequest {
    private String caseId;
    private String executedBy;
    private long current = 1;
    private long pageSize = 10;
}
