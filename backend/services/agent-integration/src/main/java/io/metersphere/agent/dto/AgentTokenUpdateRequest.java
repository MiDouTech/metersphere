package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentTokenUpdateRequest {
    @NotBlank
    private String id;
    private String name;
    private String projectId;
    private String scopes;
    private Long expireTime;
    private Boolean enable;
}
