package io.metersphere.plan.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class TestPlanDocumentResponse {

    @Schema(description = "测试计划ID")
    private String testPlanId;

    @Schema(description = "文档内容")
    private String content;

    @Schema(description = "内容类型 RICH_TEXT/MARKDOWN")
    private String contentType;

    @Schema(description = "是否已存在文档记录")
    private Boolean exists;

    @Schema(description = "更新时间")
    private Long updateTime;

    @Schema(description = "更新人")
    private String updateUser;

    @Schema(description = "模板元数据（首次套模板用）")
    private TemplateMeta templateMeta;

    @Data
    public static class TemplateMeta {
        @Schema(description = "所属项目名称")
        private String projectName;

        @Schema(description = "测试计划名称")
        private String planName;

        @Schema(description = "编制人")
        private String author;

        @Schema(description = "编制日期 yyyy-MM-dd")
        private String date;

        @Schema(description = "文档编号")
        private String docNo;
    }
}
