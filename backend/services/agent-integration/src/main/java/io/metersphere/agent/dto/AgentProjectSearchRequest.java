package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AgentProjectSearchRequest {
    @Schema(description = "Project keyword. Matches project id, project name, or project number shown as ID in the UI.")
    private String keyword;

    @Schema(description = "Max returned projects. Default 50, max 200.")
    @Min(1)
    @Max(200)
    private Integer limit;
}
