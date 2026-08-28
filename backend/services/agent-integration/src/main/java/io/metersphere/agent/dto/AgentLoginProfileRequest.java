package io.metersphere.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentLoginProfileRequest {
    @NotBlank private String projectId;
    @NotBlank private String environmentProfileId;
    @NotBlank private String name;
    @NotBlank private String loginType;
    @NotBlank private String loginUrl;
    @NotBlank private String usernameLocator;
    @NotBlank private String passwordLocator;
    @NotBlank private String submitLocator;
    @NotBlank private String successAssertion;
    private String sessionValidation;
    @NotBlank private String mfaPolicy;
    @Min(1000) @Max(60000) private Integer timeoutMs;
    private Boolean enabled;
    private Integer version;
}
