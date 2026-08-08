package io.metersphere.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentExecutionResolveResponse {
    private String status;
    private boolean executable;
    private boolean confirmationRequired;
    private String confirmationReason;
    private String projectId;
    private String testPlanId;
    private String selectionMode;
    private AgentSearchFilters resolvedFilter;
    private String caseSnapshotHash;
    private Double parseConfidence;
    private List<String> matchedReasons = new ArrayList<>();
    private String message;
    private int total;
    private Integer estimatedMinutes;
    private boolean highRisk;
    private List<String> highRiskSignals = new ArrayList<>();
    private List<AgentTestPlanDTO> candidatePlans = new ArrayList<>();
    private List<AgentCaseDTO> cases = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
