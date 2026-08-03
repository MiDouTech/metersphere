package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AgentProjectSearchRequest {
    @Schema(description = "Project keyword. Matches project id, project name, or project number shown as ID in the UI.")
    private String keyword;

    @Schema(description = "Page number, starting from 1. Default 1.")
    @Min(1)
    private Integer page;

    @Schema(description = "Page size. Default 20, max 100.")
    @Min(1)
    @Max(100)
    private Integer pageSize;

    @Schema(description = "Legacy limit. Prefer pageSize. Max 100 when used alone.")
    @Min(1)
    @Max(200)
    private Integer limit;

    @Schema(description = "When false (default), exclude disabled projects. Deleted projects are always excluded.")
    private Boolean includeArchived;
}
