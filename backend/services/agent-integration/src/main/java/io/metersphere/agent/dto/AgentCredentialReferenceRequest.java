package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentCredentialReferenceRequest {
    @NotBlank private String projectId;
    @NotBlank private String environmentId;
    @NotBlank @Size(max = 255) private String name;
    @NotBlank private String credentialType;
    @NotBlank @Size(max = 64) private String businessRole;
    @NotBlank private String providerType;
    @NotBlank @Size(max = 1024) private String secretRef;
    @Size(max = 255) private String usernameHint;
    private Long expiresAt;
    @NotNull private Boolean enabled;
    private Integer version;
}
