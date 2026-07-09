package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentTokenCreateResponse {
    private String id;
    private String name;
    private String token;
    private String scopes;
    private Long expireTime;
    private String warning = "Token 明文仅展示一次，请妥善保存";
}
