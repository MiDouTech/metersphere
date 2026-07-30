package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentBugSearchResponse {
    @Schema(description = "总数")
    private long total;

    @Schema(description = "警告")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "缺陷列表")
    private List<AgentBugDTO> bugs = new ArrayList<>();
}
