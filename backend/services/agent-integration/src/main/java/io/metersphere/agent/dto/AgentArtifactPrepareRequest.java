package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgentArtifactPrepareRequest {
    @NotBlank private String taskId;
    @NotBlank private String executionId;
    @NotBlank private String leaseId;
    @NotBlank private String leaseToken;
    private String caseId;
    private String stepId;
    @NotBlank private String purpose;
    @NotBlank private String fileName;
    @NotBlank private String contentType;
    @NotNull private Long sizeBytes;
    @NotBlank private String sha256;
    @NotBlank private String requestId;
    private String traceId;
    private Boolean redacted;
}
