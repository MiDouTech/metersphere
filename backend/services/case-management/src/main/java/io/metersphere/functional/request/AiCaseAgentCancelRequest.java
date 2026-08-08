package io.metersphere.functional.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiCaseAgentCancelRequest {
    @NotBlank
    private String projectId;
    @NotBlank
    private String requestId;
}
