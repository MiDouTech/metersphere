package io.metersphere.system.dto.ai.agent;

import lombok.Data;

@Data
public class AiAgentBridgePackageDTO {
    private String id;
    private String version;
    private String osType;
    private String architecture;
    private String fileName;
    private String storage;
    private String storageFolder;
    private String sha256;
    private Long sizeBytes;
    private String status;
    private String description;
    private Long downloadCount;
    private String createUser;
    private Long createTime;
    private String updateUser;
    private Long updateTime;
}
