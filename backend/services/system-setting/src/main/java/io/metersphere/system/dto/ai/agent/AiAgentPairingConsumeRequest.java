package io.metersphere.system.dto.ai.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiAgentPairingConsumeRequest {
    @NotBlank
    @Pattern(regexp = "[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}")
    private String pairingCode;
    @NotBlank
    @Size(max = 255)
    private String deviceName;
    @NotBlank
    @Size(max = 16384)
    private String publicKey;
    @NotBlank
    @Size(max = 128)
    private String certificateFingerprint;
    @NotBlank
    @Size(max = 64)
    private String bridgeVersion;
    @NotBlank
    @Size(max = 32)
    private String protocolVersion;
    @NotBlank
    @Size(max = 32)
    private String osType;
}
