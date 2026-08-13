package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentTaskTriggerHistoryDTO {
    private String id;
    private String triggerId;
    private String taskId;
    private String eventId;
    private Long scheduledAt;
    private Long fireTime;
    private String status;
    private String message;
    private Long createdAt;
}
