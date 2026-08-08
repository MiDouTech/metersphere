package io.metersphere.functional.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class FunctionalCaseAiDraftBatchSaveRequest {
    @NotBlank
    @Schema(description = "Project ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String projectId;

    @Schema(description = "Default module ID")
    private String moduleId;

    @Schema(description = "Default template ID")
    private String templateId;

    @NotEmpty
    @Schema(description = "Draft IDs", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> draftIds;

    @AssertTrue(message = "保存正式用例前必须由当前用户明确确认")
    @NotNull(message = "保存确认标记不能为空")
    @Schema(description = "Current user explicitly confirmed saving drafts as formal cases", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean confirmed;
}
