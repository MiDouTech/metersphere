package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentWebLocatorDTO {
    private String strategy;
    private String testId;
    private String role;
    private String name;
    private String label;
    private String placeholder;
    private String text;
    private String selector;
}
