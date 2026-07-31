package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FunctionalCaseTestPlanOverviewDTO {

    @Schema(description = "功能用例 ID")
    private String caseId;

    @Schema(description = "测试计划 ID")
    private String id;

    @Schema(description = "测试计划业务编号")
    private Long num;

    @Schema(description = "测试计划名称")
    private String name;

    @Schema(description = "测试计划状态")
    private String status;

    @Schema(description = "是否已归档")
    private Boolean archived;

    @Schema(description = "计划内已执行用例数")
    private Long executed;

    @Schema(description = "计划内用例总数")
    private Long total;

    @Schema(description = "计划执行进度百分比")
    private Integer rate;

    @Schema(description = "更新时间")
    private Long updateTime;
}
