package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AgentExecutionPreflightRequest {
    @NotBlank private String projectId;
    private String testPlanId;
    @Size(max = 100) private List<String> caseIds;
    private Map<String, Object> caseFilter;
    @Size(max = 100) private List<String> expandedCaseIds;
    private Map<String, String> expansionReasons;
    @NotBlank private String environmentProfileId;
    private String credentialReferenceId;
    private String modelProfileId;
    private String promptTemplateId;
    @NotBlank private String runnerType;
    @Size(max = 32) private List<String> requiredCapabilities;
    private Map<String, Object> policy;
    @NotBlank private String taskOrigin;
    @Size(max = 3) private List<String> responsibleUserIds;
}
