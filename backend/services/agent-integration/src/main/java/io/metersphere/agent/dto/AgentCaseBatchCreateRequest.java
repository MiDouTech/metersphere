package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AgentCaseBatchCreateRequest {
    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String projectId;

    @Schema(description = "模块ID；与 modulePath 二选一或同时（优先 moduleId）")
    private String moduleId;

    @Schema(description = "模块路径，如 登录/短信登录；无 moduleId 时按路径创建")
    private String modulePath;

    @Schema(description = "模板ID；为空则使用项目默认功能用例模板")
    private String templateId;

    @Schema(description = "用例列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty
    @Valid
    private List<AgentCaseCreateItem> cases;

    @Schema(description = "遇错是否立即失败，默认 false")
    private Boolean failFast = false;
}
