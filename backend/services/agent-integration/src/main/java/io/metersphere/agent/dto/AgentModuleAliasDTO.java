package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentModuleAliasDTO {
    private String id;
    @NotBlank
    private String projectId;
    @NotBlank
    private String alias;
    @NotBlank
    private String moduleId;
    private Long createTime;
}
