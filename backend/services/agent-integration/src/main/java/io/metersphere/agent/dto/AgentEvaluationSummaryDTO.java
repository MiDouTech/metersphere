package io.metersphere.agent.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentEvaluationSummaryDTO {
    private String executorType;
    private String executorId;
    private Long sampleCount;
    private Long successfulRuns;
    private Long productFailures;
    private Long environmentFailures;
    private Long dataFailures;
    private Long agentFailures;
    private Long blockedRuns;
    private BigDecimal averageCompletionRate;
    private BigDecimal averageEvidenceRate;
    private BigDecimal averageDurationMs;
    private BigDecimal averageManualScore;
}
