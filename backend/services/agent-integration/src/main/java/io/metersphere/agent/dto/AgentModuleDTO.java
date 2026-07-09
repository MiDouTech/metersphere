package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AgentModuleDTO {
    @Schema(description = "模块 ID")
    private String id;

    @Schema(description = "模块名称")
    private String name;

    @Schema(description = "模块路径")
    private String path;

    @Schema(description = "父模块 ID")
    private String parentId;
}
