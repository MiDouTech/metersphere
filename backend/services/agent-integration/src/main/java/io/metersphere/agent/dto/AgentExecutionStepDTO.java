package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentExecutionStepDTO {
    private String id;
    private String taskId;
    private String executionCaseId;
    private String caseId;
    private String sourceStepId;
    private Integer pos;
    private String instruction;
    private String expected;
    private String actionJson;
    private String assertionJson;
    private String riskLevel;
    private Boolean retryable;
    private String status;
    private String actualResult;
    private String errorMessage;
    private String failureCategory;
    private Integer attempt;
    private Integer retryCount;
    private Boolean healed;
    private Long startedAt;
    private Long finishedAt;
    private Long createTime;
    private Long updateTime;
    private Integer version;
}
