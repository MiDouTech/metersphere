package io.metersphere.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AgentCredentialVerifyResult {
    private boolean valid;
    private String status;
    private String message;
    private String traceId;
}
