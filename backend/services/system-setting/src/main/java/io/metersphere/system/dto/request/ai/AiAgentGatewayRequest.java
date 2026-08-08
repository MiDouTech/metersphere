package io.metersphere.system.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiAgentGatewayRequest {
    private String id;
    @NotBlank private String name;
    @NotBlank private String protocol;
    @NotBlank private String baseUrl;
    private String authType = "BEARER";
    private String authToken;
    private String organizationId;
    private String projectId;
    private boolean personal;
    private List<String> capabilities = new ArrayList<>();
}
