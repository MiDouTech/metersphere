package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AgentTaskTriggerEventRequest {
    @NotBlank
    private String eventType;
    private Map<String, Object> payload = new LinkedHashMap<>();
}
