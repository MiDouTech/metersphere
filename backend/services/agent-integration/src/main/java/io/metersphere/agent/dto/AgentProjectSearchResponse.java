package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentProjectSearchResponse {
    @Schema(description = "Matched projects for current page")
    private List<AgentProjectDTO> items = new ArrayList<>();

    @Schema(description = "Current page, starting from 1")
    private int page;

    @Schema(description = "Page size")
    private int pageSize;

    @Schema(description = "Total matched projects")
    private long total;

    @Schema(description = "Whether more pages exist")
    private boolean hasMore;
}
