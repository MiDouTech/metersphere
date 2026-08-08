package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentWebActionDTO {
    private String contractVersion = "v1";
    private String type;
    private AgentWebLocatorDTO target;
    private String value;
    private String valueRef;
    private Integer timeoutMs = 10000;
    private Boolean retryable = true;
    private String riskLevel = "LOW";
}
