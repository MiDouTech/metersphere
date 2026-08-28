package io.metersphere.agent.service.gateway;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class GatewayPlanningResponse {
    private String gatewayRequestId;
    private String content;
    private Map<String, Object> structuredOutput;
    private Map<String, Object> resolvedOffering;
    private String modelVersion;
    private String finishReason;
    private Usage usage = new Usage();
    private Cost cost = new Cost();
    private Integer retries;
    private Long ttftMs;
    private Long durationMs;

    @Data public static class Usage {
        private Long inputTokens = 0L;
        private Long outputTokens = 0L;
        private Long reasoningTokens = 0L;
        private Long cachedTokens = 0L;
    }
    @Data public static class Cost {
        private BigDecimal amount;
        private String currency;
    }
}
