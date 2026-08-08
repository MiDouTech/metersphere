package io.metersphere.system.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AiAgentGatewayInvokeRequest {
    @NotBlank private String gatewayId;
    @NotBlank private String projectId;
    @NotBlank private String operation;
    private String taskId;
    private Map<String, Object> context = new HashMap<>();
}
