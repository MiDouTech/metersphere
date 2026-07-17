package io.metersphere.functional.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FunctionalTestReportUpdateRequest {

    @Schema(description = "报告ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 50)
    private String id;

    @Schema(description = "报告名称")
    @Size(max = 255)
    private String name;

    @Schema(description = "报告正文 JSON 分节字符串")
    private String content;
}
