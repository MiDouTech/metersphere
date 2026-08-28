package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentCredentialResolveRequest {
    @NotBlank private String leaseId;
    @NotBlank @Size(max = 64) private String purpose;
    @NotBlank @Size(max = 8192) private String runnerPublicKey;
}
