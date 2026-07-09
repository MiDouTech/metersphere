package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentCaseSearchResponse {
    @Schema(description = "命中策略")
    private List<String> matchedBy = new ArrayList<>();

    @Schema(description = "命中模块路径")
    private List<String> matchedModules = new ArrayList<>();

    @Schema(description = "命中模块 ID")
    private List<String> matchedModuleIds = new ArrayList<>();

    @Schema(description = "总数")
    private long total;

    @Schema(description = "警告信息")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "用例列表")
    private List<AgentCaseDTO> cases = new ArrayList<>();
}
