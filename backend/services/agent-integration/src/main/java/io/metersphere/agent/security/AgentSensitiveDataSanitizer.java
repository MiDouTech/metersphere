package io.metersphere.agent.security;

/** Conservative redaction for executor-provided summaries persisted as audit data. */
public final class AgentSensitiveDataSanitizer {
    private AgentSensitiveDataSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("(?i)bearer\\s+[a-z0-9._~-]+", "Bearer ***")
                .replaceAll("(?i)(authorization|cookie|set-cookie|password|passwd|token|secret|api[-_]?key)\\s*[:=]\\s*[^,;\\s}]+", "$1=***");
    }

    /** Parse first: redacting serialized JSON can consume its closing quotes. */
    public static String sanitizeJson(String value) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(value);
            return mapper.writeValueAsString(redact(node, mapper));
        } catch (Exception error) {
            throw new io.metersphere.sdk.exception.MSException("AGENT_FACTS_INVALID");
        }
    }

    private static com.fasterxml.jackson.databind.JsonNode redact(com.fasterxml.jackson.databind.JsonNode node,
            com.fasterxml.jackson.databind.ObjectMapper mapper) {
        if (node.isObject()) {
            var result = mapper.createObjectNode();
            node.fields().forEachRemaining(field -> result.set(field.getKey(),
                    field.getKey().matches("(?i)authorization|cookie|set-cookie|password|passwd|token|secret|api[-_]?key")
                            ? mapper.getNodeFactory().textNode("***") : redact(field.getValue(), mapper)));
            return result;
        }
        if (node.isArray()) {
            var result = mapper.createArrayNode();
            node.forEach(item -> result.add(redact(item, mapper)));
            return result;
        }
        return node.isTextual() ? mapper.getNodeFactory().textNode(sanitize(node.asText())) : node;
    }
}
