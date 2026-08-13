package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentRunnerLeaseDTO {
    private String id;
    private String taskId;
    private String runnerId;
    private String executorType;
    private String executorId;
    private Integer attempt;
    private String status;
    private String leaseTokenHash;
    private Long acceptedTime;
    private Long expireTime;
    private Long lastHeartbeatTime;
    private Long lastEventSequence;
    private Long createTime;
    private Long updateTime;
    private Integer version;
}
