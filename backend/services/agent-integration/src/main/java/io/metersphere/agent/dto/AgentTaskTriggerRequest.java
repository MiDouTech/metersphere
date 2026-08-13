package io.metersphere.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

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
    @Valid
    @NotNull
    private AgentExecutionCreateRequest taskTemplate;
}
