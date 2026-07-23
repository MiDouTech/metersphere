package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AgentTestPlanAssociateRequest {
    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String projectId;

    @Schema(description = "测试计划ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String testPlanId;

    @Schema(description = "用例ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty
    private List<String> caseIds;

    @Schema(description = "测试集ID，可选")
    private String collectionId;
}
