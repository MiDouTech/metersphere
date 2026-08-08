package io.metersphere.system.dto.request.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiProjectGovernanceDTO {
    @NotBlank
    private String projectId;
    private List<String> allowedModelIds = new ArrayList<>();
    private List<String> allowedResourceTypes = new ArrayList<>(List.of("MODEL_API"));
    private List<String> allowedAgentProviders = new ArrayList<>();
    private boolean allowPersonalAgent;
    private boolean allowLocalAgentTools;
    @Min(1) @Max(20)
    private int maxAgentConcurrentTasks = 1;
    @Min(1) @Max(240)
    private int maxAgentExecutionMinutes = 15;
    @Min(1) @Max(10000)
    private int dailyAgentExecutionLimit = 50;
    private String fallbackModelId;
    @Min(1) @Max(100)
    private int maxConcurrentTasks = 3;
    @Min(1)
    private long monthlyTokenQuota = 1_000_000L;
    @Min(1)
    private long projectFileQuota = 1_073_741_824L;
    @Min(1) @Max(1000)
    private int sessionFileLimit = 20;
    @Min(1)
    private long singleFileLimit = 52_428_800L;
    private long usedTokens;
    private long usedFileBytes;
    private int activeTasks;
}
