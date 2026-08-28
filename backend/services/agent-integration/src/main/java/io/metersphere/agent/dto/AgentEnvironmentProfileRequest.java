package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AgentEnvironmentProfileRequest {
    @NotBlank private String projectId;
    @NotBlank private String environmentId;
    @NotBlank @Size(max = 255) private String name;
    @NotBlank @Size(max = 2048) private String baseUrl;
    @NotEmpty private List<@NotBlank String> allowedOrigins;
    @Size(max = 64) private String networkZone;
    @NotBlank private String environmentType;
    private String loginProfileId;
    private String defaultCredentialReferenceId;
    @NotBlank private String runnerType;
    private List<String> requiredCapabilities;
    @NotNull private Boolean enabled;
    private Integer version;
}
