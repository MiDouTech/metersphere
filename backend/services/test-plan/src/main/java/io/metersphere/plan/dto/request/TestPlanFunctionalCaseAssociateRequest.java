package io.metersphere.plan.dto.request;

import io.metersphere.functional.dto.BaseFunctionalCaseBatchDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 测试计划详情 - 关联功能用例请求（参考用例评审关联）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TestPlanFunctionalCaseAssociateRequest extends BaseFunctionalCaseBatchDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{test_plan.id.not_blank}")
    @Schema(description = "测试计划ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String testPlanId;

    @NotBlank(message = "{project.id.not_blank}")
    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String projectId;

    @Schema(description = "测试集ID，为空时使用计划下默认功能用例测试集")
    private String collectionId;
}
