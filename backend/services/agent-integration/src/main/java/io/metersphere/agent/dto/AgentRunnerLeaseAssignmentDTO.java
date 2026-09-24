package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentRunnerLeaseAssignmentDTO {
    private io.metersphere.agent.quality.GlobalQualityGate.Binding qualityPolicy;
    private String leaseId;
    private String leaseToken;
    private Long expireTime;
    private Long nextEventSequence;
    private AgentExecutionTaskDTO task;
}
