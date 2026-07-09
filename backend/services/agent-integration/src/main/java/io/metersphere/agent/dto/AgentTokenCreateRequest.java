package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentTokenCreateRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String userId;
    private String projectId;
    @NotBlank
    private String scopes;
    private Long expireTime;
}
