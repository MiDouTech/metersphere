package io.metersphere.agent.security;

/** Conservative redaction for executor-provided summaries persisted as audit data. */
public final class AgentSensitiveDataSanitizer {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    private static final int MAX_DEPTH = 8;
    private static final int MAX_CHARACTERS = 2 * 1024 * 1024;
    private AgentSensitiveDataSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return redactText(value, 0);
    }

    public static boolean containsSensitive(String value) {
        return value != null && !value.equals(redactText(value, 0));
    }

    private static boolean sensitiveKey(String key) {
        return key.matches("(?i)authorization|cookie|set-cookie|password|passwd|token|secret|api[-_]?key");
    }

    private static void checkLimits(String value, int depth) {
        if (depth > MAX_DEPTH || value.length() > MAX_CHARACTERS)
            throw new io.metersphere.sdk.exception.MSException("AGENT_FACTS_INVALID");
    }

    private static String redactText(String value, int depth) {
        checkLimits(value, depth);
        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) {
            com.fasterxml.jackson.databind.JsonNode parsed = null;
            try { parsed = MAPPER.readTree(value); }
            catch (com.fasterxml.jackson.core.exc.StreamConstraintsException unsafe) {
                throw new io.metersphere.sdk.exception.MSException("AGENT_FACTS_INVALID");
            }
            catch (java.io.IOException ignored) { /* Plain text fallback. */ }
            if (parsed != null) {
                var redacted = redact(parsed, MAPPER, depth + 1);
                if (parsed.equals(redacted)) return value;
                try { return MAPPER.writeValueAsString(redacted); }
                catch (java.io.IOException error) { throw new io.metersphere.sdk.exception.MSException("AGENT_FACTS_INVALID"); }
            }
        }
        return value
                .replaceAll("(?i)bearer\\s+[a-z0-9._~-]+", "Bearer ***")
                .replaceAll("(?i)([\"']?(?:authorization|cookie|set-cookie|password|passwd|token|secret|api[-_]?key)[\"']?\\s*[:=]\\s*)[\"'][^\"']*[\"']", "$1\"***\"")
                .replaceAll("(?i)(authorization|cookie|set-cookie|password|passwd|token|secret|api[-_]?key)\\s*[:=]\\s*[^,;\\s}]+", "$1=***");
    }

    /** Parse first: redacting serialized JSON can consume its closing quotes. */
    public static String sanitizeJson(String value) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            checkLimits(value, 0);
            var node = mapper.readTree(value);
            return mapper.writeValueAsString(redact(node, mapper, 0));
        } catch (Exception error) {
            throw new io.metersphere.sdk.exception.MSException("AGENT_FACTS_INVALID");
        }
    }

    private static com.fasterxml.jackson.databind.JsonNode redact(com.fasterxml.jackson.databind.JsonNode node,
            com.fasterxml.jackson.databind.ObjectMapper mapper, int depth) {
        if (depth > MAX_DEPTH) throw new io.metersphere.sdk.exception.MSException("AGENT_FACTS_INVALID");
        if (node.isObject()) {
            var result = mapper.createObjectNode();
            node.fields().forEachRemaining(field -> result.set(field.getKey(),
                    sensitiveKey(field.getKey())
                            ? mapper.getNodeFactory().textNode("***") : redact(field.getValue(), mapper, depth + 1)));
            return result;
        }
        if (node.isArray()) {
            var result = mapper.createArrayNode();
            node.forEach(item -> result.add(redact(item, mapper, depth + 1)));
            return result;
        }
        return node.isTextual() ? mapper.getNodeFactory().textNode(redactText(node.asText(), depth)) : node;
    }
}
