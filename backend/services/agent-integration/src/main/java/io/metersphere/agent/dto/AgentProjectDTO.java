package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AgentProjectDTO {
    @Schema(description = "项目ID")
    private String id;
    @Schema(description = "项目名称")
    private String name;
    @Schema(description = "组织ID")
    private String organizationId;
    @Schema(description = "描述")
    private String description;
    @Schema(description = "是否启用")
    private Boolean enable;
}
