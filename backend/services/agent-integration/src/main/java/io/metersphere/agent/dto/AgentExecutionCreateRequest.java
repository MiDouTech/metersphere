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
    @Schema(description = "任务名称")
    private String name;
    @Schema(description = "任务目标及完成预期")
    private String objective;
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
    @Schema(description = "审批与高风险动作策略 JSON")
    private String approvalPolicy;
    @Schema(description = "执行器必须具备的能力编码")
    private List<String> requiredCapabilities;
    @Schema(description = "显式选择并冻结到任务上下文的测试资产引用")
    private List<TestAssetRefDTO> assetRefs;
    private String environmentId;
    private String targetUrl;
    private String browserType;
    private String loginMode;
    private String providerId;
    @NotBlank
    @Schema(description = "通过执行前检查取得的一次性 Preflight ID")
    private String preflightId;
    private String environmentProfileId;
    private String credentialReferenceId;
    private String modelProfileId;
    private String promptTemplateVersionId;
    private String runnerId;
    @Schema(description = "执行方式：RUNNER/AGENT，默认 RUNNER")
    private String executionMode;
    @Schema(description = "Agent 调度方式：PUSH/PULL；Runner 固定为 PULL")
    private String dispatchMode;
    @Schema(description = "Agent 类型：WORKBUDDY/CURSOR/CODEX；executionMode=AGENT 时必填")
    private String agentType;
    private String executedBy;
    @Schema(description = "幂等键")
    private String idempotencyKey;
    @Schema(description = "是否已确认大范围/高风险任务")
    private Boolean confirmed;
    @Schema(description = "计划外项目级全量执行（须 confirmed=true 且 caseIds 为空）")
    private Boolean projectWide;
    @Schema(description = "任务超时时间，epoch milliseconds")
    private Long timeoutAt;
    @Schema(description = "租约失败最大尝试次数，默认 3，最大 10")
    private Integer maxAttempts;
}
