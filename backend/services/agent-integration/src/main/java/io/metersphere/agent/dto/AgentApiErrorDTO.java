package io.metersphere.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class AgentApiErrorDTO {
    private String code;
    private String message;
    private Map<String, Object> details;
    private String traceId;
}
