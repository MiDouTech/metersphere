package io.metersphere.agent.dto;

import io.metersphere.agent.constants.AgentConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AgentBugSearchRequest {
    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String projectId;

    @Schema(description = "关键词：标题/编号/标签")
    private String query;

    @Schema(description = "状态过滤，如 open/closed 等平台状态值")
    private List<String> status;

    @Schema(description = "处理人用户ID列表")
    private List<String> handleUserIds;

    @Min(1)
    @Schema(description = "当前页")
    private int current = 1;

    @Min(1)
    @Max(value = AgentConstants.MAX_PAGE_SIZE, message = "pageSize max 100")
    @Schema(description = "每页条数，默认 50，最大 100")
    private int pageSize = AgentConstants.DEFAULT_PAGE_SIZE;
}
