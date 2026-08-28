package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class AgentCaseExecutabilityRequest {
    @NotBlank private String projectId;
    @NotEmpty @Size(max=100) private List<@NotBlank String> caseIds;
    @NotBlank private String environmentProfileId;
}
