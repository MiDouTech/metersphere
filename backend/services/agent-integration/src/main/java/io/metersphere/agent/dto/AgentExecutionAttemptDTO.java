package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentExecutionAttemptDTO {
    private String id;
    private String taskId;
    private Integer attemptNo;
    private String executorChannel;
    private String executorType;
    private String executorId;
    private String leaseId;
    private String status;
    private String traceId;
    private String errorCode;
    private String errorMessage;
    private Long startTime;
    private Long finishTime;
    private Long createTime;
    private Long updateTime;
    private Integer version;
}
