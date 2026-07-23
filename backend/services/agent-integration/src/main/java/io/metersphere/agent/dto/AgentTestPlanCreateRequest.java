package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AgentTestPlanCreateRequest {
    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String projectId;

    @Schema(description = "测试计划名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "模块ID，默认 root")
    private String moduleId;

    @Schema(description = "创建后关联的用例ID列表")
    private List<String> caseIds;

    @Schema(description = "是否自动更新功能用例状态")
    private Boolean automaticStatusUpdate = false;

    @Schema(description = "是否允许重复添加用例")
    private Boolean repeatCase = false;

    @Schema(description = "通过阈值 0-100")
    private Double passThreshold = 100.0;
}
