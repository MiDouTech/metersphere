package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FunctionalTestReportResultCountDTO {
    @Schema(description = "执行结果")
    private String result;
    @Schema(description = "数量")
    private Long cnt;
}
