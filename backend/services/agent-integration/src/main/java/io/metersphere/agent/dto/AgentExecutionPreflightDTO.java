package io.metersphere.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class AgentExecutionPreflightDTO {
    private String id;
    private String projectId;
    private String taskOrigin;
    private String status;
    private List<AgentPreflightCheckDTO> checks;
    private List<String> resolvedCaseIds;
    private List<String> originalCaseIds;
    private List<String> addedCaseIds;
    private Map<String, String> reasonByCase;
    private Integer originalScopeCount;
    private Integer expandedScopeCount;
    private BigDecimal scopeExpansionRate;
    private String snapshotHash;
    private String promptTemplateVersionId;
    private String blockedReason;
    private String blockedDetail;
    private String traceId;
    private Long expiresAt;
}
