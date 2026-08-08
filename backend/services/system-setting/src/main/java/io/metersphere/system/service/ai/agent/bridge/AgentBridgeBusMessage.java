package io.metersphere.system.service.ai.agent.bridge;

import lombok.Data;

@Data
public class AgentBridgeBusMessage {
    private String kind;
    private String originNode;
    private String targetNode;
    private String deviceId;
    private AgentBridgeEnvelope envelope;

    public static AgentBridgeBusMessage downstream(String originNode, String targetNode, String deviceId,
                                                    AgentBridgeEnvelope envelope) {
        AgentBridgeBusMessage message = new AgentBridgeBusMessage();
        message.kind = "DOWNSTREAM";
        message.originNode = originNode;
        message.targetNode = targetNode;
        message.deviceId = deviceId;
        message.envelope = envelope;
        return message;
    }

    public static AgentBridgeBusMessage upstream(String originNode, String targetNode, String deviceId,
                                                  AgentBridgeEnvelope envelope) {
        AgentBridgeBusMessage message = new AgentBridgeBusMessage();
        message.kind = "UPSTREAM";
        message.originNode = originNode;
        message.targetNode = targetNode;
        message.deviceId = deviceId;
        message.envelope = envelope;
        return message;
    }
}
