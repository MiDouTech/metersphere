package io.metersphere.agent.constants;

import io.metersphere.sdk.exception.MSException;

public final class AgentExecutorChannel {
    public static final String MODEL_API_RUNNER = "MODEL_API_RUNNER";
    public static final String EXTERNAL_MCP_AGENT = "EXTERNAL_MCP_AGENT";

    private AgentExecutorChannel() {
    }

    public static void requireLegal(String origin, String channel) {
        AgentTaskOrigin.requireValid(origin);
        boolean legal = AgentTaskOrigin.PERSONAL_MCP.equals(origin)
                ? EXTERNAL_MCP_AGENT.equals(channel)
                : MODEL_API_RUNNER.equals(channel);
        if (!legal) {
            throw new MSException("TASK_ORIGIN_CHANNEL_MISMATCH");
        }
    }
}
