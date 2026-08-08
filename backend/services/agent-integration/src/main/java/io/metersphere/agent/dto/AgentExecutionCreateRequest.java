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
    @Schema(description = "选择方式：MANUAL/NATURAL_LANGUAGE")
    private String selectionMode;
    @Schema(description = "脱敏后的原始提示词")
    private String prompt;
    @Schema(description = "服务端确认过的结构化筛选 DSL JSON")
    private String resolvedFilter;
    @Schema(description = "执行、自愈、截图和风险策略快照 JSON")
    private String policySnapshot;
    private String environmentId;
    private String targetUrl;
    private String browserType;
    private String loginMode;
    private String providerId;
    private String runnerId;
    @Schema(description = "执行方式：RUNNER/AGENT，默认 RUNNER")
    private String executionMode;
    @Schema(description = "Agent 类型：WORKBUDDY/CURSOR/CODEX；executionMode=AGENT 时必填")
    private String agentType;
    private String executedBy;
    @Schema(description = "幂等键")
    private String idempotencyKey;
    @Schema(description = "是否已确认大范围/高风险任务")
    private Boolean confirmed;
    @Schema(description = "计划外项目级全量执行（须 confirmed=true 且 caseIds 为空）")
    private Boolean projectWide;
}
