package io.metersphere.system.dto.ai.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiAgentPairingCreateRequest {
    @NotBlank
    @Pattern(regexp = "WORKBUDDY|CODEX|CURSOR")
    private String provider;
    @Size(max = 255)
    private String expectedDeviceName;
}
