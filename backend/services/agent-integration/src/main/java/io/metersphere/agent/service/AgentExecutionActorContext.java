package io.metersphere.agent.service;

import org.apache.commons.lang3.StringUtils;

/** Explicit actor for scheduler/event jobs that run without a Shiro web session. */
public final class AgentExecutionActorContext {
    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();

    private AgentExecutionActorContext() {
    }

    public static void bind(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("Background execution actor is required");
        }
        ACTOR.set(userId);
    }

    public static String get() {
        return ACTOR.get();
    }

    public static void clear() {
        ACTOR.remove();
    }
}
