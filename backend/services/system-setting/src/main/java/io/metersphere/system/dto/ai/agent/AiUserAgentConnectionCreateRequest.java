package io.metersphere.system.dto.ai.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiUserAgentConnectionCreateRequest {
    @NotBlank
    @Pattern(regexp = "WORKBUDDY|CODEX|CURSOR")
    private String provider;
    @NotBlank
    @Size(max = 50)
    private String deviceId;
    @Size(max = 255)
    private String displayName;
}
