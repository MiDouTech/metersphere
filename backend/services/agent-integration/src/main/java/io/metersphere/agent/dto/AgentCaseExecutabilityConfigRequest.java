package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class AgentCaseExecutabilityConfigRequest {
    @NotBlank private String projectId;
    @NotBlank private String caseId;
    @NotBlank private String environmentProfileId;
    @Size(max=64) private String credentialRole;
    @Size(max=100) private List<@NotBlank String> pageObjectIds;
    @Size(max=100) private List<@NotBlank String> datasetIds;
    private String businessFlowId;
    private String riskLevel;
    private Integer version;
}
