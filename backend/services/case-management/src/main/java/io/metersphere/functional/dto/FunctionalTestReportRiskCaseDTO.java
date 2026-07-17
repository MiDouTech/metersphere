package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FunctionalTestReportRiskCaseDTO {
    @Schema(description = "用例ID")
    private String caseId;
    @Schema(description = "用例编号")
    private Long num;
    @Schema(description = "用例名称")
    private String name;
    @Schema(description = "最近执行结果")
    private String lastExecResult;
}
