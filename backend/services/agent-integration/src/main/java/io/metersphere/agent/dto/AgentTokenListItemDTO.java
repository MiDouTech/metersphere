package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentTokenListItemDTO {
    private String id;
    private String name;
    private String userId;
    private String projectId;
    private String scopes;
    private Long expireTime;
    private Boolean enable;
    private Long createTime;
    private String createUser;
}
