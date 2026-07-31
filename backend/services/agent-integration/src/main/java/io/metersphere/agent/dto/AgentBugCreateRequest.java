package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AgentBugCreateRequest {
    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String projectId;

    @Schema(description = "缺陷标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String title;

    @Schema(description = "缺陷描述")
    private String description;

    @Schema(description = "模板ID；为空则用项目默认缺陷模板")
    private String templateId;

    @Schema(description = "标签")
    private List<String> tags;

    @Schema(description = "关联功能用例ID")
    private String caseId;

    @Schema(description = "用例类型，默认 FUNCTIONAL")
    private String caseType;

    @Schema(description = "测试计划ID")
    private String testPlanId;

    @Schema(description = "测试计划用例关联ID")
    private String testPlanCaseId;

    @Schema(description = "自定义字段 fieldId -> value")
    private Map<String, String> customFields;

    @Schema(description = "临时附件 ID 列表（purpose=BUG_DETAIL）")
    private List<String> attachmentIds;

    @Schema(description = "额外关联用例 ID 列表")
    private List<String> addCaseIds;

    @Schema(description = "幂等键")
    private String requestId;
}
