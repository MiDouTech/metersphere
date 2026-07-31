package io.metersphere.agent.tool;

import java.util.Map;

public interface AgentMcpToolHandler {
    String name();

    default String description() {
        return name();
    }

    String requiredScope();

    Map<String, Object> inputSchema();

    Map<String, Object> annotations();

    Object execute(Map<String, Object> arguments);
}
