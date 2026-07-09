package io.metersphere.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AgentBatchSubmitRequest {
    @NotBlank
    private String projectId;
    private String testPlanId;
    private String executedBy;
    private boolean failFast;
    @NotEmpty
    @Valid
    private List<AgentCaseSubmitRequest> results;
}
