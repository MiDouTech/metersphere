package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AgentTokenUpdateRequest {
    @NotBlank
    private String id;
    private String name;
    private List<String> projectIds;
    private String projectId;
    private String scopes;
    private String clientType;
    private Long expireTime;
    private Boolean enable;
}
