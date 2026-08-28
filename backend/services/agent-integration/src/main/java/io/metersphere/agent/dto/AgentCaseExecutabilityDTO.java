package io.metersphere.agent.dto;

import lombok.Data;
import java.util.List;

@Data
public class AgentCaseExecutabilityDTO {
    private String id;
    private String projectId;
    private String caseId;
    private String environmentProfileId;
    private String automationReadiness;
    private String credentialRole;
    private List<String> pageObjectIds;
    private List<String> datasetIds;
    private String businessFlowId;
    private String riskLevel;
    private List<String> missingItems;
    private Long lastCheckedAt;
    private String checkerVersion;
    private Integer version;
}
