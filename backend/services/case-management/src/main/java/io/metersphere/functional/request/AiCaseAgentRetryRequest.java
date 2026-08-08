package io.metersphere.functional.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiCaseAgentRetryRequest {
    @NotBlank
    private String projectId;
    @NotBlank
    private String requestId;
    @Size(max = 64)
    private String newRequestId;
}
