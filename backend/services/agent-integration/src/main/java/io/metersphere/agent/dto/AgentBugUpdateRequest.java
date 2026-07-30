package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AgentBugUpdateRequest {
    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String projectId;

    @Schema(description = "缺陷ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String bugId;

    @Schema(description = "标题；空则不改")
    private String title;

    @Schema(description = "描述；空则不改")
    private String description;

    @Schema(description = "标签；null 不改，空数组清空")
    private List<String> tags;

    @Schema(description = "模板ID；空则沿用原模板")
    private String templateId;

    @Schema(description = "自定义字段 fieldId -> value（含状态/处理人等模板字段）")
    private Map<String, String> customFields;
}
