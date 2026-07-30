package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FunctionalCaseOverviewDTO {

    @Schema(description = "关联评审摘要")
    private List<FunctionalCaseReviewOverviewDTO> reviews = new ArrayList<>();

    @Schema(description = "关联测试计划摘要")
    private List<FunctionalCaseTestPlanOverviewDTO> testPlans = new ArrayList<>();

    @Schema(description = "当前用户个人执行进度")
    private FunctionalCasePersonalProgressDTO personalProgress;
}
