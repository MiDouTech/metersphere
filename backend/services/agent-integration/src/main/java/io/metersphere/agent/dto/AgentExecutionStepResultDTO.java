package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentExecutionStepResultDTO {
    private String id;
    private String taskId;
    private String executionId;
    private String leaseId;
    private String stepId;
    private Integer attempt;
    private String status;
    private String inputSnapshot;
    private String outputSummary;
    private String assertionResult;
    private String errorCode;
    private String errorMessage;
    private String artifactIds;
    private String requestId;
    private String traceId;
    private Long startedAt;
    private Long finishedAt;
    private Long createTime;
    private String createUser;
}
