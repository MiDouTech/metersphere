package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FunctionalTestReportDTO {

    @Schema(description = "报告ID")
    private String id;

    @Schema(description = "项目ID")
    private String projectId;

    @Schema(description = "报告名称")
    private String name;

    @Schema(description = "关联测试计划ID")
    private String planId;

    @Schema(description = "报告正文 JSON 分节字符串")
    private String content;

    @Schema(description = "统计快照 JSON 字符串")
    private String statsSnapshot;

    @Schema(description = "创建时间")
    private Long createTime;

    @Schema(description = "更新时间")
    private Long updateTime;

    @Schema(description = "创建人")
    private String createUser;

    @Schema(description = "更新人")
    private String updateUser;
}
