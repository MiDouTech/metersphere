package io.metersphere.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentHumanRequestDTO {
    private String id;
    private String requestKey;
    private String taskId;
    private String projectId;
    private String requestType;
    private String title;
    private String content;
    private String riskLevel;
    private String status;
    private String requestedBy;
    private String assignedTo;
    private String response;
    private String respondedBy;
    private Long respondedAt;
    private Long expiresAt;
    private Long createdAt;
    private Long updatedAt;
    private Integer resolutionVersion;
    private String resolvedReason;
    private String checkpointId;
    private String traceId;
    private List<String> recipientUserIds;
}
