package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentWebAssertionDTO {
    private String contractVersion = "v1";
    private String type;
    private AgentWebLocatorDTO target;
    private String operator;
    private String expected;
    private String attribute;
    private Integer timeoutMs = 10000;
}
