package io.metersphere.agent.tool;

import java.util.Map;

public interface AgentMcpToolHandler {
    String name();

    default String description() {
        return name();
    }

    String requiredScope();

    Map<String, Object> inputSchema();

    default Map<String, Object> outputSchema() {
        return Map.of("type", "object", "properties", Map.of("result", Map.of()),
                "required", java.util.List.of("result"), "additionalProperties", false);
    }

    Map<String, Object> annotations();

    default boolean readOnlyHint() { return Boolean.TRUE.equals(annotations().get("readOnlyHint")); }
    default boolean destructiveHint() { return Boolean.TRUE.equals(annotations().get("destructiveHint")); }
    default boolean idempotentHint() { return Boolean.TRUE.equals(annotations().get("idempotentHint")); }
    default boolean openWorldHint() { return Boolean.TRUE.equals(annotations().get("openWorldHint")); }

    Object execute(Map<String, Object> arguments);
}
