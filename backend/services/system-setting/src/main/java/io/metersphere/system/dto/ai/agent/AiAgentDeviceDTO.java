package io.metersphere.system.dto.ai.agent;

import lombok.Data;

@Data
public class AiAgentDeviceDTO {
    private String id;
    private String deviceName;
    private String status;
    private String bridgeVersion;
    private String protocolVersion;
    private String osType;
    private Long lastHeartbeatTime;
    private Long createTime;
}
