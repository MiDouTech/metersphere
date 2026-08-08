package io.metersphere.system.dto.ai.agent;

import lombok.Data;

@Data
public class AiUserAgentConnectionDTO {
    private String id;
    private String provider;
    private String connectionMode;
    private String displayName;
    private String maskedAccount;
    private String status;
    private String capabilities;
    private String deviceId;
    private String deviceName;
    private String deviceStatus;
    private String bridgeVersion;
    private Long expiresAt;
    private Long lastHealthTime;
    private Long createTime;
}
