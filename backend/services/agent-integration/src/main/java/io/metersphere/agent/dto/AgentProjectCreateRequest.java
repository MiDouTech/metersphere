package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AgentProjectCreateRequest {
    @Schema(description = "组织ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String organizationId;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String name;

    @Schema(description = "项目描述")
    private String description;

    @Schema(description = "成员用户ID列表（至少包含创建者）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty
    private List<String> userIds;

    @Schema(description = "模块设置")
    private List<String> moduleIds;

    @Schema(description = "资源池ID")
    private List<String> resourcePoolIds;

    @Schema(description = "是否启用全部资源池")
    private Boolean allResourcePool;
}
