package io.metersphere.system.dto.ai.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiAgentConnectionStatusRequest {
    @NotBlank
    @Size(max = 50)
    private String deviceId;
    @NotBlank
    @Size(max = 50)
    private String connectionId;
    @NotBlank
    @Pattern(regexp = "CONNECTED|OFFLINE|AUTH_EXPIRED")
    private String status;
    @Size(max = 255)
    private String maskedAccount;
    @Size(max = 16000)
    private String capabilities;
    private Long expiresAt;
}
