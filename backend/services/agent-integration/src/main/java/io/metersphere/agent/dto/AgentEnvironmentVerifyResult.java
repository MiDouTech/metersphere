package io.metersphere.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentEnvironmentVerifyResult {
    private boolean valid;
    private boolean reachable;
    private boolean originAllowed;
    private boolean dnsResolved;
    private boolean tlsValid;
    private boolean runnerMatched;
    private List<String> checks = new ArrayList<>();
    private String traceId;
}
