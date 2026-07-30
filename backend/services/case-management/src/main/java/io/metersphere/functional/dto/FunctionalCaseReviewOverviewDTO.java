package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FunctionalCaseReviewOverviewDTO {

    @Schema(description = "评审 ID")
    private String id;

    @Schema(description = "评审业务编号")
    private Long num;

    @Schema(description = "评审名称")
    private String name;

    @Schema(description = "当前用例在该评审中的状态")
    private String caseStatus;

    @Schema(description = "评审任务状态")
    private String reviewStatus;

    @Schema(description = "是否已归档")
    private Boolean archived;

    @Schema(description = "更新时间")
    private Long updateTime;
}
