package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentTaskTriggerHistoryDTO {
    private String id;
    private String triggerId;
    private Integer triggerVersion;
    private String taskId;
    private String eventId;
    private Long scheduledAt;
    private Integer attemptNo;
    private String idempotencyKey;
    private String traceId;
    private Long fireTime;
    private String status;
    private String blockedReason;
    private String message;
    private Long createdAt;
}
