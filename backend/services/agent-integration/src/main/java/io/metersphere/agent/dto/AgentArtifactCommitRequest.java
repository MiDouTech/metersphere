package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentArtifactCommitRequest {
    @NotBlank private String taskId;
    @NotBlank private String executionId;
    @NotBlank private String leaseId;
    @NotBlank private String leaseToken;
    @NotBlank private String artifactId;
    @NotBlank private String uploadToken;
    @NotBlank private String requestId;
    private String traceId;
}
