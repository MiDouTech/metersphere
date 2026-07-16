package io.metersphere.system.dto;

import io.metersphere.system.domain.SystemParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社区版界面配置（替代企业版 xpack DisplayService）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DisplayDTO extends SystemParameter {
    @Schema(description = "文件原始名 / 预览路径")
    private String fileName;

    @Schema(description = "兼容前端字段")
    private String file;

    @Schema(description = "是否恢复默认图")
    private Boolean original;

    @Schema(description = "是否上传了新文件")
    private Object hasFile;
}
