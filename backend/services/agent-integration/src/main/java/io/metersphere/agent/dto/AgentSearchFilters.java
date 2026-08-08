package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class AgentSearchFilters {
    @Schema(description = "名称或编号关键词")
    private String keyword;

    @Schema(description = "优先级，如 P0")
    private List<String> priority;

    @Schema(description = "最近执行结果")
    private List<String> lastExecuteResult;

    @Schema(description = "标签")
    private List<String> tags;

    @Schema(description = "模块 ID 列表")
    private List<String> moduleIds;

    @Schema(description = "是否排除高风险动作")
    private Boolean excludeRiskActions;

    @Schema(description = "结果上限，最大 100")
    private Integer limit;
}
