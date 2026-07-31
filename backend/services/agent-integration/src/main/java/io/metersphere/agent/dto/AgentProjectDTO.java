package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AgentProjectDTO {
    @Schema(description = "Internal project ID")
    private String id;

    @Schema(description = "Project number shown as ID in the UI")
    private Long num;

    @Schema(description = "Project name")
    private String name;

    @Schema(description = "Organization ID")
    private String organizationId;

    @Schema(description = "Organization name")
    private String organizationName;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Enabled")
    private Boolean enable;
}
