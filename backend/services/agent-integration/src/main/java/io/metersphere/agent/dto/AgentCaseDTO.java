package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentCaseDTO {
    @Schema(description = "功能用例 ID")
    private String caseId;

    @Schema(description = "用例编号")
    private Long num;

    @Schema(description = "用例名称")
    private String name;

    @Schema(description = "模块路径")
    private String modulePath;

    @Schema(description = "编辑模式 STEP/TEXT")
    private String caseEditType;

    @Schema(description = "标签")
    private List<String> tags;

    @Schema(description = "优先级")
    private String priority;

    @Schema(description = "前置条件")
    private String prerequisite;

    @Schema(description = "测试计划 ID")
    private String testPlanId;

    @Schema(description = "计划关联 ID，回写时映射 TestPlanCaseRunRequest.id")
    private String testPlanCaseId;

    @Schema(description = "最近执行结果")
    private String lastExecuteResult;

    @Schema(description = "步骤列表")
    private List<AgentCaseStepDTO> steps = new ArrayList<>();
}
