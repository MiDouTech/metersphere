package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentRunnerDTO {
    private String id;
    private String organizationId;
    private String name;
    private String runnerVersion;
    private String contractVersion;
    private String status;
    private String operatingSystem;
    private String browserCapabilities;
    private String environmentLabels;
    private String authTokenHash;
    private Integer maxConcurrency;
    private Integer activeCount;
    private Long lastHeartbeatTime;
    private Long createTime;
    private Long updateTime;
    private String createUser;
    private String updateUser;
}
