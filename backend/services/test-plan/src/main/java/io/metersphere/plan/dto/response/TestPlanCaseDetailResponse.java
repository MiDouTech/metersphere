package io.metersphere.plan.dto.response;

import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author wx
 */
@Data
public class TestPlanCaseDetailResponse extends FunctionalCaseDetailDTO {
    @Schema(description = "当前有效执行的来源；不替代本计划历史执行")
    private java.util.Map<String, String> executionRef;

    @Schema(description = "执行来源的完整事实，包括重复及额外步骤；定义与执行事实分别保留")
    private java.util.Map<String, Object> executionFacts;

    private String syncState;

    @Schema(description = "用例缺陷列表总数量")
    private Integer bugListCount;

    @Schema(description = "用例执行历史总数量")
    private Integer runListCount;
}
