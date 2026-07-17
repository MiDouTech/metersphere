package io.metersphere.functional.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FunctionalTestReportGenerateRequest {

    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{functional_case.project_id.not_blank}")
    @Size(max = 50)
    private String projectId;

    @Schema(description = "报告名称，可选，默认带日期")
    @Size(max = 255)
    private String name;

    @Schema(description = "测试计划ID，可选")
    @Size(max = 50)
    private String planId;

    @Schema(description = "统计开始时间，MVP 可忽略")
    private Long startTime;

    @Schema(description = "统计结束时间，MVP 可忽略")
    private Long endTime;
}
