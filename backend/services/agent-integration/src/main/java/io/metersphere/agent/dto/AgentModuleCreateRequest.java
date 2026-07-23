package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentModuleCreateRequest {
    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String projectId;

    @Schema(description = "模块名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String name;

    @Schema(description = "父模块ID，默认 NONE（根）")
    private String parentId;

    @Schema(description = "模块路径，如 登录/短信登录；若提供则按路径逐级创建，忽略 name/parentId")
    private String modulePath;
}
