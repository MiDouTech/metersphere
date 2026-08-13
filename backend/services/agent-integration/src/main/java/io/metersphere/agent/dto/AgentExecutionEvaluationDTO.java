package io.metersphere.agent.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentExecutionEvaluationDTO {
    private String id;
    private String taskId;
    private String projectId;
    private String executorType;
    private String executorId;
    private String operationalStatus;
    private String businessVerdict;
    private BigDecimal completionRate;
    private BigDecimal evidenceRate;
    private Integer healingCount;
    private Integer retryCount;
    private Long durationMs;
    private BigDecimal manualScore;
    private String manualComment;
    private String evaluatedBy;
    private Long evaluatedAt;
    private Long createdAt;
    private Long updatedAt;
}
