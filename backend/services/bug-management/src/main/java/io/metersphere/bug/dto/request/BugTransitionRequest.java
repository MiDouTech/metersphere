package io.metersphere.bug.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BugTransitionRequest {
    @NotBlank
    private String transitionId;
    @NotBlank
    private String targetStatusId;
    @NotNull
    private Long expectedUpdateTime;
    private String comment;
    private Boolean override;
    private String overrideReason;
}
