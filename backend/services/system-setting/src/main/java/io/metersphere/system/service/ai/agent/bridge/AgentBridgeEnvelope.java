package io.metersphere.system.service.ai.agent.bridge;

import lombok.Data;

import java.util.Map;

@Data
public class AgentBridgeEnvelope {
    private String protocolVersion;
    private String type;
    private String requestId;
    private long sequence;
    private long timestamp;
    private String nonce;
    private Map<String, Object> payload;
}
