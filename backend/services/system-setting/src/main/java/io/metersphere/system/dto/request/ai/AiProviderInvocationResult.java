package io.metersphere.system.dto.request.ai;

import lombok.Data;

@Data
public class AiProviderInvocationResult {
    private String content;
    private String modelSourceId;
    private String providerName;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private boolean tokenEstimated;
    private long durationMs;
    private boolean fallbackUsed;
}
