package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentExecutionOperationsDTO {
    private String organizationId;
    private String health;
    private Integer onlineRunnerCount;
    private Integer staleRunnerCount;
    private Integer activeLeaseCount;
    private Integer queuedTaskCount;
    private Integer stuckTaskCount;
    private Integer writebackBacklogCount;
    private Integer artifactBacklogCount;
    private Integer expiredArtifactCount;
    private Long generatedAt;
}
