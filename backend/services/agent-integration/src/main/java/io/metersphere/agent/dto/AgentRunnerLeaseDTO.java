package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentRunnerLeaseDTO {
    private String id;
    private String taskId;
    private String executionId;
    private String executorChannel;
    private String runnerId;
    private String executorType;
    private String executorId;
    private String leaseOwnerType;
    private String leaseOwnerId;
    private Integer attempt;
    private String status;
    private String leaseTokenHash;
    private Long acceptedTime;
    private Long expireTime;
    private Long lastHeartbeatTime;
    private Long lastEventSequence;
    private String releasedReason;
    private Long closedAt;
    private Long createTime;
    private Long updateTime;
    private Integer version;
}
