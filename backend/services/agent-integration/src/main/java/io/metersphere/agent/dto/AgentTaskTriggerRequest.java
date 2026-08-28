package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AgentTaskTriggerRequest {
    @NotBlank
    private String projectId;
    @NotBlank
    @Size(max = 255)
    private String name;
    @NotBlank
    private String triggerType;
    private String cronExpression;
    private String timezone;
    private String eventType;
    private String eventFilter;
    private String concurrencyPolicy;
    private String missedPolicy;
    private Boolean enabled;
    @NotNull
    private AgentExecutionCreateRequest taskTemplate;
    private String modelProfileId;
    private String promptTemplateId;
    @NotBlank
    private String environmentProfileId;
    private String credentialReferenceId;
    @NotBlank
    private String runnerType;
    private List<String> requiredCapabilities;
    private Map<String, Object> policy;
    private Map<String, Object> evidencePolicy;
    private Map<String, Object> notificationPolicy;
    @Size(min = 3, max = 3)
    private List<String> responsibleUserIds;
}
