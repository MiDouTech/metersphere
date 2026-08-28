package io.metersphere.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentEnvironmentProfileDTO {
    private String id;
    private String organizationId;
    private String projectId;
    private String environmentId;
    private String name;
    private String baseUrl;
    private List<String> allowedOrigins;
    private String networkZone;
    private String environmentType;
    private String loginProfileId;
    private String defaultCredentialReferenceId;
    private String runnerType;
    private List<String> requiredCapabilities;
    private Boolean productionAllowed;
    private Boolean enabled;
    private Integer version;
    private String createUser;
    private String updateUser;
    private Long createTime;
    private Long updateTime;
}
