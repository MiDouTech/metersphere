package io.metersphere.agent.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentExecutionHealingDTO {
    private String id;
    private String taskId;
    private String executionCaseId;
    private String executionStepId;
    private Integer attempt;
    private String failureType;
    private String originalLocator;
    private String candidateLocators;
    private String selectedLocator;
    private String reason;
    private BigDecimal confidence;
    private String result;
    private String beforeArtifactId;
    private String afterArtifactId;
    private Long durationMs;
    private Long createTime;
    private String createUser;
}
