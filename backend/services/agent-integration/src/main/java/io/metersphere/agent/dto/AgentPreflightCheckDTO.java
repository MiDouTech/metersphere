package io.metersphere.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentPreflightCheckDTO {
    private String code;
    private String status;
    private String message;
    private Map<String, Object> details;
    private Long checkedAt;
}
