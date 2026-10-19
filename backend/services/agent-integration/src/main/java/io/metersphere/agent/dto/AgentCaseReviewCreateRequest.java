package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AgentCaseReviewCreateRequest {
    @NotBlank
    @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_.:-]{1,128}")
    private String requestId;

    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String projectId;

    @Schema(description = "评审名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @jakarta.validation.constraints.Size(max=255)
    private String name;

    @Schema(description = "评审模块ID，默认 root")
    private String moduleId;

    @Schema(description = "通过标准 SINGLE/MULTIPLE，默认 SINGLE")
    @jakarta.validation.constraints.Pattern(regexp="SINGLE|MULTIPLE")
    private String reviewPassRule;

    @Schema(description = "评审人用户ID；为空则使用当前 Agent Token 用户")
    @jakarta.validation.constraints.Size(max=100)
    private List<String> reviewers;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "创建时关联的用例ID")
    @jakarta.validation.constraints.Size(max=100)
    private List<String> caseIds;

    @Schema(description = "标签")
    @jakarta.validation.constraints.Size(max=100)
    private List<String> tags;
}
