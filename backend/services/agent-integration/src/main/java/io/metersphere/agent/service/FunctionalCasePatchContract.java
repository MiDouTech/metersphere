package io.metersphere.agent.service;

import io.metersphere.agent.tool.AgentMcpToolSchemas;
import java.util.*;

/** Shared discovery/runtime contract. Business permissions, template and version checks remain in the service. */
public final class FunctionalCasePatchContract {
    private FunctionalCasePatchContract() { }
    public static final List<String> FIELDS = List.of("name", "moduleId", "prerequisite", "description", "tags", "steps", "customFields", "priority");
    private static final Set<String> STEP_FIELDS = Set.of("id", "num", "desc", "expected", "actualResult", "executeResult");

    private static Map<String,Object> nullable(Map<String,Object> schema) {
        return Map.of("anyOf", List.of(schema, Map.of("type", "null")));
    }

    public static Map<String,Object> schema() {
        var text = Map.<String,Object>of("type", "string");
        var step = AgentMcpToolSchemas.object(Map.of(
                "id", nullable(text), "num", nullable(Map.of("type", "integer")),
                "desc", nullable(text), "expected", nullable(text),
                "actualResult", nullable(text), "executeResult", nullable(text)), List.of());
        step.put("description", "Definition steps. Legacy actualResult/executeResult fields are accepted for compatibility; use execution.submit to publish execution results.");
        var properties = new LinkedHashMap<String,Object>();
        properties.put("name", AgentMcpToolSchemas.string(1,255));
        properties.put("moduleId", nullable(text));
        properties.put("prerequisite", nullable(text));
        properties.put("description", nullable(text));
        properties.put("tags", nullable(Map.of("type","array","items",text)));
        properties.put("steps", nullable(Map.of("type","array","items",step)));
        properties.put("customFields", nullable(AgentMcpToolSchemas.objectAny()));
        properties.put("priority", nullable(text));
        var result = AgentMcpToolSchemas.object(properties,List.of());
        result.put("minProperties",1);
        return result;
    }

    public static void validate(Object input) {
        if (!(input instanceof Map<?,?> patch) || patch.isEmpty()) invalid("patch");
        Map<?,?> patch = (Map<?,?>)input;
        if (!FIELDS.containsAll(patch.keySet())) {
            // Do not echo attacker-controlled keys; safe supported fields are enough to repair input.
            var details = new LinkedHashMap<String,Object>();
            details.put("field", "patch"); details.put("allowedFields", FIELDS);
            if (patch.keySet().stream().anyMatch(Set.of("actualResult","executeResult","lastExecuteResult")::contains))
                details.put("recommendedTool", "metersphere.functional.execution.submit");
            throw new AgentFieldValidationException("CASE_PATCH_FIELD_UNSUPPORTED",details);
        }
        for (var entry : patch.entrySet()) {
            String field = (String)entry.getKey(); Object value = entry.getValue();
            if (field.equals("name")) {
                if (!(value instanceof String name) || name.isBlank() || name.length()>255) invalid(field);
            } else if (Set.of("moduleId","prerequisite","description","priority").contains(field)) {
                if (value!=null && !(value instanceof String)) invalid(field);
            } else if (field.equals("tags")) {
                if (value!=null && (!(value instanceof List<?> list) || list.stream().anyMatch(v -> !(v instanceof String)))) invalid(field);
            } else if (field.equals("customFields")) {
                if (value!=null && (!(value instanceof Map<?,?> map) || map.keySet().stream().anyMatch(key -> !(key instanceof String)))) invalid(field);
            } else if (field.equals("steps") && value!=null) {
                if (!(value instanceof List<?>)) invalid(field);
                for (Object item : (List<?>)value) {
                    if (!(item instanceof Map<?,?> step) || !STEP_FIELDS.containsAll(step.keySet())) invalid("steps");
                    for (var e : ((Map<?,?>)item).entrySet()) {
                        Object v=e.getValue();
                        if (v!=null && (e.getKey().equals("num") ? !(v instanceof Number n) || n.doubleValue()!=n.intValue() : !(v instanceof String))) invalid("steps");
                    }
                }
            }
        }
    }

    private static void invalid(String field) {
        throw new AgentFieldValidationException("CASE_PATCH_TYPE_INVALID",Map.of("field",field,"allowedFields",FIELDS));
    }
}
