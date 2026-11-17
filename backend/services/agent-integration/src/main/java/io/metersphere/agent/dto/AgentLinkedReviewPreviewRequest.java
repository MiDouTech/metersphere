package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class AgentLinkedReviewPreviewRequest extends AgentCaseReviewCreateRequest {
    @NotBlank @Size(max=50)
    private String planId;
}
