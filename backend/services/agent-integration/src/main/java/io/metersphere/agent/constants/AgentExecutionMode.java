package io.metersphere.agent.constants;

import java.util.List;
import java.util.Locale;

public final class AgentExecutionMode {
    public static final String RUNNER = "RUNNER";
    public static final String AGENT = "AGENT";
    public static final List<String> SUPPORTED_AGENTS = List.of("WORKBUDDY", "CURSOR", "CODEX");

    private AgentExecutionMode() {
    }

    public static String normalizeMode(String value) {
        return value == null || value.isBlank() ? RUNNER : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String normalizeAgentType(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
