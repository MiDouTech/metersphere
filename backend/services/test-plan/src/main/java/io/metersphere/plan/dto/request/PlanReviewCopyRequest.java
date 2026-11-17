package io.metersphere.plan.dto.request;

import io.metersphere.functional.planreview.PlanReviewSettings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlanReviewCopyRequest {
    @NotBlank @Size(max = 50)
    private String id;
    @NotBlank @Size(max = 128)
    private String requestKey;
    private PlanReviewSettings reviewSettings;
}
