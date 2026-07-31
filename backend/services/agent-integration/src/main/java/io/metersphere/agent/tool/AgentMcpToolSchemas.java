package io.metersphere.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentMcpToolSchemas {
    private AgentMcpToolSchemas() {
    }

    public static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    public static Map<String, Object> string(int minLength, int maxLength) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("minLength", minLength);
        schema.put("maxLength", maxLength);
        return schema;
    }

    public static Map<String, Object> stringArray(int maxItems) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", string(1, 128));
        schema.put("maxItems", maxItems);
        return schema;
    }

    public static Map<String, Object> enumString(List<String> values) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("enum", values);
        return schema;
    }

    public static Map<String, Object> annotations(String scope, boolean readOnly, boolean destructive, boolean idempotent) {
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("scope", scope);
        annotations.put("readOnlyHint", readOnly);
        annotations.put("destructiveHint", destructive);
        annotations.put("idempotentHint", idempotent);
        return annotations;
    }
}
