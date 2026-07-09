package io.metersphere.agent.dto;

import io.metersphere.agent.constants.AgentConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AgentCaseSearchRequest {
    @Schema(description = "自然语言检索片段")
    private String query;

    @Schema(description = "是否返回完整步骤，默认 true")
    private Boolean includeSteps = true;

    @Schema(description = "测试计划 ID")
    private String testPlanId;

    @Schema(description = "结构化过滤条件")
    private AgentSearchFilters filters;

    @Min(1)
    @Schema(description = "当前页")
    private int current = 1;

    @Min(1)
    @Max(value = AgentConstants.MAX_PAGE_SIZE, message = "pageSize max 500")
    @Schema(description = "每页条数")
    private int pageSize = 50;
}
