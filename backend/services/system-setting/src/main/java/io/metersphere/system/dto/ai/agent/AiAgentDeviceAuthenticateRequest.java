package io.metersphere.system.dto.ai.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiAgentDeviceAuthenticateRequest {
    @NotBlank
    @Size(max = 50)
    private String deviceId;
    @NotBlank
    @Size(max = 50)
    private String challengeId;
    @NotBlank
    @Size(max = 256)
    private String nonce;
    @NotBlank
    @Size(max = 4096)
    private String signature;
}
