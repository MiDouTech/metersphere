package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AgentExecutionCreateRequest {
    @NotBlank
    @Schema(description = "项目 ID、项目编号或精确项目名")
    private String projectId;
    @Schema(description = "测试计划 ID；为空表示计划外执行")
    private String testPlanId;
    @Schema(description = "明确选择的功能用例 ID 列表")
    private List<String> caseIds;
    @Schema(description = "来源：MCP/CASE_LIST/WORKBENCH/API")
    private String source;
    private String environmentId;
    private String targetUrl;
    private String browserType;
    private String loginMode;
    private String providerId;
    private String runnerId;
    private String executedBy;
    @Schema(description = "幂等键")
    private String idempotencyKey;
    @Schema(description = "是否已确认大范围/高风险任务")
    private Boolean confirmed;
}
