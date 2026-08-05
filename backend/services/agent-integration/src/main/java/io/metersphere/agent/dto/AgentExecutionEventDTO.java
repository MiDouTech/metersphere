package io.metersphere.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentExecutionEventDTO {
    private String id;
    private String taskId;
    private String caseId;
    private String stepId;
    private Long sequence;
    private Long eventTime;
    private String level;
    private String eventType;
    private String message;
    private List<String> artifactIds;
    private String sanitizedMetadata;
    private String createUser;
}
