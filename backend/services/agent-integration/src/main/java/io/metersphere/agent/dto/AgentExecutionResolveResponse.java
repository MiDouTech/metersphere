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
    private String message;
    private int total;
    private List<AgentTestPlanDTO> candidatePlans = new ArrayList<>();
    private List<AgentCaseDTO> cases = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
