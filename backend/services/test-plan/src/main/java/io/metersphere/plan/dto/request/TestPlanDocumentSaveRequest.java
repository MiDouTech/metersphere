package io.metersphere.plan.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TestPlanDocumentSaveRequest {

    @Schema(description = "文档内容")
    private String content;

    @Schema(description = "内容类型 RICH_TEXT/MARKDOWN")
    @Size(max = 20)
    private String contentType = "RICH_TEXT";
}
