package io.metersphere.agent.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentEvaluationHistoryDTO {
    private String id;
    private String taskId;
    private String projectId;
    private BigDecimal score;
    private String comment;
    private String evaluatedBy;
    private Long evaluatedAt;
}
