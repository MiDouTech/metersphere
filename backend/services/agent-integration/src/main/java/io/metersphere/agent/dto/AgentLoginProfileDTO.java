package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentLoginProfileDTO extends AgentLoginProfileRequest {
    private String id;
    private String organizationId;
    private Long createTime;
    private Long updateTime;
}
