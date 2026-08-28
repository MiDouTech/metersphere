package io.metersphere.agent.service.gateway;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GatewayPlanningRequest {
    private String appCaller;
    private String logicalModelPublicId;
    private String promptPolicyId;
    private String promptVersionId;
    private List<Map<String, Object>> messages;
    private Map<String, Object> outputSchema;
    private Double temperature;
    private Integer maxOutputTokens;
    private Integer timeoutMs;
    private String idempotencyKey;
    private String traceId;
    private Map<String, Object> metadata;
}
