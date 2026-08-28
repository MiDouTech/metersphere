package io.metersphere.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentExecutionEventDTO {
    private String id;
    private String contractVersion;
    private String eventId;
    private String taskId;
    private String executionId;
    private String leaseId;
    private String caseId;
    private String stepId;
    private Integer attempt;
    private Long sequence;
    private Long eventTime;
    private String level;
    private String eventType;
    private String actorType;
    private String actorId;
    private String toolName;
    private String requestId;
    private String traceId;
    private String message;
    private List<String> artifactIds;
    /** JSON persisted by MyBatis; API callers continue to use artifactIds. */
    private String artifactIdsJson;
    private String sanitizedMetadata;
    private String payload;
    private String createUser;
}
