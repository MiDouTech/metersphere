package io.metersphere.agent.dto;import jakarta.validation.constraints.NotBlank;import lombok.Data;
@Data public class AgentPageElementRequest {@NotBlank private String name;@NotBlank private String strategy;@NotBlank private String selectorValue;private String fallbackLocators;private Boolean sensitive;private String riskLevel;private Integer version;}
