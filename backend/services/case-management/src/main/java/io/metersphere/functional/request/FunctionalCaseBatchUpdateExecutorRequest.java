package io.metersphere.functional.request;

import io.metersphere.functional.dto.BaseFunctionalCaseBatchDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author wx
 */
@Data
public class FunctionalCaseBatchUpdateExecutorRequest extends BaseFunctionalCaseBatchDTO {

    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{functional_case.project_id.not_blank}")
    private String projectId;

    @Schema(description = "执行人id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{test_plan.user_id.not_blank}")
    private String userId;
}
