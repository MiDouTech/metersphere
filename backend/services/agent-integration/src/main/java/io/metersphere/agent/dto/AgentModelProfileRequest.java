package io.metersphere.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AgentModelProfileRequest {
    @NotBlank private String projectId;
    @NotBlank @Size(max = 255) private String name;
    @NotBlank private String gatewayAppCaller;
    @NotBlank private String gatewayServiceKeyRef;
    @NotBlank private String logicalModelPublicId;
    @NotBlank private String promptPolicyId;
    private String gatewayPromptPolicyId;
    private List<String> requiredCapabilities;
    @Min(1000) @Max(300000) private Integer requestTimeoutMs;
    @Min(1) @Max(65536) private Integer maxOutputTokens;
    private BigDecimal maxCostAmount;
    private String currency;
    private Boolean enabled;
    private Integer version;
}
