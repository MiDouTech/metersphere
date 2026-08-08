package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentRunnerRegisterResponse {
    private String runnerId;
    private String runnerToken;
    private String contractVersion;
    private Long createTime;
}
