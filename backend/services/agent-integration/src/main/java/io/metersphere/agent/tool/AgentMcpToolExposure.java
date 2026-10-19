package io.metersphere.agent.tool;

/** The personal MCP catalog never advertises legacy direct result writes or trigger administration. */
public final class AgentMcpToolExposure {
    private AgentMcpToolExposure() {}
    public static boolean legacyResultWrite(String name) {
        return "metersphere.functional.submit".equals(name) || "metersphere.functional.submit.batch".equals(name);
    }
    public static boolean discoverable(String name) {
        return !legacyResultWrite(name) && !name.startsWith("metersphere.execution.trigger.");
    }
}
