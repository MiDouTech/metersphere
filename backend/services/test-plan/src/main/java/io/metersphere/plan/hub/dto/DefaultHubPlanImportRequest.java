package io.metersphere.plan.hub.dto;

import io.metersphere.sdk.constants.DefaultHubConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DefaultHubPlanImportRequest {
    @NotBlank @jakarta.validation.constraints.Size(max = 128)
    private String requestKey;
    private io.metersphere.functional.planreview.PlanReviewSettings reviewSettings;
    @NotBlank
    @Schema(description = "枢纽源计划ID")
    private String sourcePlanId;

    @NotBlank
    @Schema(description = "目标业务项目ID")
    private String targetProjectId;

    @NotBlank
    @Schema(description = "SKIP|OVERWRITE")
    private String conflictStrategy = DefaultHubConstants.CONFLICT_SKIP;
}
