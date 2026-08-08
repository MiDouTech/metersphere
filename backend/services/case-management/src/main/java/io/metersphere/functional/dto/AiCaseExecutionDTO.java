package io.metersphere.functional.dto;

import lombok.Data;

@Data
public class AiCaseExecutionDTO {
    private String id;
    private String requestId;
    private String conversationId;
    private String projectId;
    private String userId;
    private String userMessageId;
    private String assistantMessageId;
    private String executionType;
    private String status;
    private String requestedModelSourceId;
    private String actualModelSourceId;
    private Boolean cancelRequested;
    private String retryOfRequestId;
    private Long inputTokens;
    private Long outputTokens;
    private Boolean tokenEstimated;
    private String errorCode;
    private String errorMessage;
    private Long startTime;
    private Long firstTokenTime;
    private Long finishTime;
    private Long durationMs;
    private Long eventSequence;
    private Long createTime;
    private Long updateTime;
}
