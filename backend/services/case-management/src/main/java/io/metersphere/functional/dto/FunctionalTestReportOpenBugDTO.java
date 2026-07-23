package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FunctionalTestReportOpenBugDTO {
    @Schema(description = "缺陷ID")
    private String bugId;
    @Schema(description = "缺陷编号")
    private Long num;
    @Schema(description = "缺陷标题")
    private String title;
    @Schema(description = "状态名称")
    private String statusName;
    @Schema(description = "处理人姓名")
    private String handleUserName;
}
