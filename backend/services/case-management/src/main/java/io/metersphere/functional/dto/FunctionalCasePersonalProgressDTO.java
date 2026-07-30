package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FunctionalCasePersonalProgressDTO {

    @Schema(description = "责任执行人为当前用户的用例总数")
    private Long total;

    @Schema(description = "已执行数量")
    private Long executed;

    @Schema(description = "通过数量")
    private Long passed;

    @Schema(description = "失败数量")
    private Long failed;

    @Schema(description = "阻塞数量")
    private Long blocked;

    @Schema(description = "跳过数量")
    private Long skipped;

    @Schema(description = "未执行数量")
    private Long unexecuted;
}
