package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AgentCaseReviewAssociateRequest {
    @NotBlank
    @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_.:-]{1,128}")
    private String requestId;

    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String projectId;

    @Schema(description = "评审ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String reviewId;

    @Schema(description = "用例ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty
    @jakarta.validation.constraints.Size(max=100)
    private List<String> caseIds;

    @Schema(description = "评审人；为空则使用当前用户")
    @jakarta.validation.constraints.Size(max=100)
    private List<String> reviewers;
}
