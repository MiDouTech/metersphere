package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AgentProjectAddMembersRequest {
    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String projectId;

    @Schema(description = "用户ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty
    private List<String> userIds;

    @Schema(description = "用户组ID列表（可选）")
    private List<String> userRoleIds;
}
