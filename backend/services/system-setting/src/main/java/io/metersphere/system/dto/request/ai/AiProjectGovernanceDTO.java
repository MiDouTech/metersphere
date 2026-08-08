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
