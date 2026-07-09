package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class AgentSearchFilters {
    @Schema(description = "优先级，如 P0")
    private List<String> priority;

    @Schema(description = "最近执行结果")
    private List<String> lastExecuteResult;

    @Schema(description = "标签")
    private List<String> tags;

    @Schema(description = "模块 ID 列表")
    private List<String> moduleIds;
}
