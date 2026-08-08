package io.metersphere.functional.dto;

import lombok.Data;

@Data
public class AiCaseMessageDTO {
    private String id;
    private String conversationId;
    private String projectId;
    private String userId;
    private String role;
    private String content;
    private String status;
    private String resourceType;
    private String resourceId;
    private String agentConnectionId;
    private String modelSourceId;
    private String requestId;
    private String toolName;
    private String toolCallId;
    private String toolArguments;
    private String toolResult;
    private Long inputTokens;
    private Long outputTokens;
    private Boolean tokenEstimated;
    private String errorCode;
    private Long createTime;
    private Long updateTime;
}
