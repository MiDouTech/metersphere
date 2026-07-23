package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AgentCaseCreateRequest {
    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String projectId;

    @Schema(description = "模块ID")
    private String moduleId;

    @Schema(description = "模块路径")
    private String modulePath;

    @Schema(description = "模板ID")
    private String templateId;

    @Schema(description = "用例名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String name;

    @Schema(description = "优先级")
    private String priority;

    @Schema(description = "标签")
    private List<String> tags;

    @Schema(description = "前置条件")
    private String prerequisite;

    @Schema(description = "备注")
    private String description;

    @Schema(description = "步骤")
    @Valid
    private List<AgentCaseStepDTO> steps;

    public AgentCaseCreateItem toItem() {
        AgentCaseCreateItem item = new AgentCaseCreateItem();
        item.setName(name);
        item.setPriority(priority);
        item.setTags(tags);
        item.setPrerequisite(prerequisite);
        item.setDescription(description);
        item.setSteps(steps);
        return item;
    }
}
