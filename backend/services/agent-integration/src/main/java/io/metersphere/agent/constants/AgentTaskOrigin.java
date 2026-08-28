package io.metersphere.agent.constants;

import io.metersphere.sdk.exception.MSException;

import java.util.Set;

public final class AgentTaskOrigin {
    public static final String PLATFORM_SCHEDULED = "PLATFORM_SCHEDULED";
    public static final String PLATFORM_MANUAL = "PLATFORM_MANUAL";
    public static final String PERSONAL_MCP = "PERSONAL_MCP";

    private static final Set<String> VALUES = Set.of(PLATFORM_SCHEDULED, PLATFORM_MANUAL, PERSONAL_MCP);

    private AgentTaskOrigin() {
    }

    public static void requireValid(String value) {
        if (!VALUES.contains(value)) {
            throw new MSException("TASK_ORIGIN_INVALID");
        }
    }
}
