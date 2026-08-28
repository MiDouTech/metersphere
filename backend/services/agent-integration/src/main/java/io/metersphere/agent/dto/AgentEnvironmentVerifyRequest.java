package io.metersphere.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentEnvironmentVerifyRequest {
    @Size(max = 2048)
    private String targetUrl;
    @Size(max = 64)
    private String runnerId;
}
