package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentCredentialReferenceDTO {
    private String id;
    private String projectId;
    private String environmentId;
    private String name;
    private String credentialType;
    private String businessRole;
    private String providerType;
    private String secretVersion;
    private String usernameHint;
    private String status;
    private Long expiresAt;
    private Long lastVerifiedAt;
    private String lastVerifyStatus;
    private String lastVerifyMessage;
    private Boolean enabled;
    private Integer version;
    private Long createTime;
    private Long updateTime;
}
