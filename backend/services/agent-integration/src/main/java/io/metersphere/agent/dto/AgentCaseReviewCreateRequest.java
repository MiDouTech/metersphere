package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AgentCaseReviewCreateRequest {
    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String projectId;

    @Schema(description = "评审名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String name;

    @Schema(description = "评审模块ID，默认 root")
    private String moduleId;

    @Schema(description = "通过标准 SINGLE/MULTIPLE，默认 SINGLE")
    private String reviewPassRule;

    @Schema(description = "评审人用户ID；为空则使用当前 Agent Token 用户")
    private List<String> reviewers;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "创建时关联的用例ID")
    private List<String> caseIds;

    @Schema(description = "标签")
    private List<String> tags;
}
