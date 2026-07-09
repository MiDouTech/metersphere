package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AgentCaseStepDTO {
    @Schema(description = "步骤 ID")
    private String id;

    @Schema(description = "步骤序号")
    private Integer num;

    @Schema(description = "步骤描述")
    private String desc;

    @Schema(description = "预期结果")
    private String expected;

    @Schema(description = "实际结果")
    private String actualResult;

    @Schema(description = "执行结果")
    private String executeResult;
}
