package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AgentCaseSubmitRequest {
    @NotBlank
    @Schema(description = "项目 ID")
    private String projectId;

    @NotBlank
    @Schema(description = "功能用例 ID")
    private String caseId;

    @Schema(description = "测试计划 ID；与 testPlanCaseId 成对出现")
    private String testPlanId;

    @Schema(description = "计划关联 ID（test_plan_functional_case.id）；与 testPlanId 成对出现")
    private String testPlanCaseId;

    @NotBlank
    @Schema(description = "最终执行结果")
    private String lastExecResult;

    @Schema(description = "Agent 标识")
    private String executedBy;

    @Schema(description = "步骤执行结果")
    private List<AgentCaseStepDTO> steps;

    @Schema(description = "执行备注")
    private String content;

    @Schema(description = "已上传附件 ID 列表（先调用 attachment/upload）")
    private List<String> attachmentIds;
}
