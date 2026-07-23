package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AgentTestPlanDTO {
    private String id;
    private String name;
    private String projectId;
    private String status;
    private Long num;
    @Schema(description = "已关联用例数（创建时传入则返回关联数）")
    private Integer associatedCaseCount;
}
