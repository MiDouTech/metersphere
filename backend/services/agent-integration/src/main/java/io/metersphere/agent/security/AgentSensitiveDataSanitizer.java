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
}
