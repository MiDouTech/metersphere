package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class AgentExecutionResolveRequest {
    @Schema(description = "自然语言执行要求")
    private String query;
    @Schema(description = "项目 ID、项目编号或精确项目名")
    private String projectId;
    private String testPlanId;
    private String testPlanName;
    private List<String> caseIds;
    private String caseKeyword;
    private Integer threshold = 20;
}
