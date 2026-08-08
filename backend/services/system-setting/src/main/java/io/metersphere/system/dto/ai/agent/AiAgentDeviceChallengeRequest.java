package io.metersphere.system.dto.ai.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiAgentDeviceChallengeRequest {
    @NotBlank
    @Size(max = 50)
    private String deviceId;
}
