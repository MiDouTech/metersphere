package io.metersphere.functional.hub.dto;

import io.metersphere.sdk.constants.DefaultHubConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class DefaultHubCaseImportRequest {
    @NotBlank
    @Schema(description = "目标业务项目ID")
    private String targetProjectId;

    @NotBlank
    @Schema(description = "CASE_IDS|MODULE_IDS|FOLDER_IDS（不再支持 ALL/UNPLANNED）")
    private String selectMode;

    @Schema(description = "选择模式对应的 ID 列表")
    private List<String> ids;

    @NotBlank
    @Schema(description = "SKIP|OVERWRITE")
    private String conflictStrategy = DefaultHubConstants.CONFLICT_SKIP;

    @Schema(description = "目标模块ID（左树选中文件夹；空则按原逻辑落到项目自建根/导入文件夹）")
    private String targetModuleId;

    @NotBlank
    @Schema(description = "客户端生成的幂等键；同一目标项目重复提交只执行一次")
    private String idempotencyKey;

    @Schema(description = "是否复制资产用例附件")
    private Boolean copyAttachments = true;
}
