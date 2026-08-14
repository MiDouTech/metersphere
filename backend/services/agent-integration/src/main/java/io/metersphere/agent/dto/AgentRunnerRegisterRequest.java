package io.metersphere.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentRunnerRegisterRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String runnerVersion;
    private String contractVersion = "v1";
    private String operatingSystem;
    private String browserCapabilities;
    private String environmentLabels;
    private String isolationMode = "UNDECLARED";
    @Min(1)
    @Max(20)
    private Integer maxConcurrency = 1;
}
