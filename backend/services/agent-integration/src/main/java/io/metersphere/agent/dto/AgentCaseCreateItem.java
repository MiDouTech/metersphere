package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AgentCaseCreateItem {
    @Schema(description = "用例名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String name;

    @Schema(description = "优先级，如 P0/P1")
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
}
