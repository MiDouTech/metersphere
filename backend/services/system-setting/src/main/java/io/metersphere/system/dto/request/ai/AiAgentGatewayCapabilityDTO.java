package io.metersphere.system.dto.request.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiAgentGatewayCapabilityDTO {
    @Schema(description = "Gateway ID")
    private String gatewayId;

    @Schema(description = "Gateway name")
    private String name;

    @Schema(description = "Protocol type")
    private String protocol;

    @Schema(description = "Whether gateway is configured")
    private boolean configured;

    @Schema(description = "Whether OAuth is supported")
    private boolean oauthSupported;

    @Schema(description = "Whether quota statistics are supported")
    private boolean quotaSupported;

    @Schema(description = "Supported features")
    private List<String> features = new ArrayList<>();

    @Schema(description = "Message")
    private String message;
}
