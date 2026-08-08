package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentRunnerLeaseAssignmentDTO {
    private String leaseId;
    private String leaseToken;
    private Long expireTime;
    private Long nextEventSequence;
    private AgentExecutionTaskDTO task;
}
