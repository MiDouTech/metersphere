package io.metersphere.system.dto.request.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiProviderCapabilityDTO {
    @Schema(description = "Model source ID")
    private String modelSourceId;

    @Schema(description = "Provider name")
    private String providerName;

    @Schema(description = "Base model name")
    private String baseName;

    @Schema(description = "Support streaming")
    private boolean streamSupported;

    @Schema(description = "Support OAuth")
    private boolean oauthSupported;

    @Schema(description = "Support agent gateway")
    private boolean agentGatewaySupported;

    @Schema(description = "Supported features")
    private List<String> features = new ArrayList<>();
}
