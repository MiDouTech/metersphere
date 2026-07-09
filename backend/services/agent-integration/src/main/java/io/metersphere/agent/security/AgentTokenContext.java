package io.metersphere.agent.security;

import io.metersphere.system.domain.AgentToken;

public class AgentTokenContext {
    private static final ThreadLocal<AgentToken> CURRENT = new ThreadLocal<>();

    public static void set(AgentToken token) {
        CURRENT.set(token);
    }

    public static AgentToken get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
