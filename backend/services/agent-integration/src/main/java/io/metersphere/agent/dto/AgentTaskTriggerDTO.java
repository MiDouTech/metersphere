package io.metersphere.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class AgentTaskTriggerDTO {
    private String id;
    private String organizationId;
    private String projectId;
    private String name;
    private String triggerType;
    private String cronExpression;
    private String timezone;
    private String eventType;
    private String eventFilter;
    @JsonIgnore
    private String webhookSecretCipher;
    private String concurrencyPolicy;
    private String missedPolicy;
    private String taskTemplate;
    private Integer triggerVersion;
    private String modelProfileId;
    private String promptTemplateId;
    private String environmentProfileId;
    private String credentialReferenceId;
    private String runnerType;
    private String requiredCapabilities;
    private String policyJson;
    private String evidencePolicyJson;
    private String notificationPolicyJson;
    private String responsibleUserIds;
    private Boolean enabled;
    private Long nextFireAt;
    private Long lastFireAt;
    private String lastFireStatus;
    private String lastError;
    private String createdBy;
    private Long createdAt;
    private String updatedBy;
    private Long updatedAt;
    private Integer version;
    private String webhookSecret;
}
