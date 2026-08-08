package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentExecutionStatus;

import java.util.Map;
import java.util.Set;

/**
 * Central transition contract for AI Web UI execution tasks.
 * Runner events describe facts; only the server uses this state machine to mutate task status.
 */
public final class AgentExecutionStateMachine {
    private static final Set<String> CANCELLABLE = Set.of(
            AgentExecutionStatus.CREATED,
            AgentExecutionStatus.RESOLVING_SCOPE,
            AgentExecutionStatus.WAITING_CONFIRMATION,
            AgentExecutionStatus.QUEUED,
            AgentExecutionStatus.PREPARING_BROWSER,
            AgentExecutionStatus.WAITING_LOGIN,
            AgentExecutionStatus.RUNNING,
            AgentExecutionStatus.PAUSED,
            AgentExecutionStatus.WRITING_BACK
    );

    private static final Map<String, Set<String>> TRANSITIONS = Map.ofEntries(
            Map.entry(AgentExecutionStatus.CREATED, Set.of(
                    AgentExecutionStatus.RESOLVING_SCOPE,
                    AgentExecutionStatus.WAITING_CONFIRMATION,
                    AgentExecutionStatus.QUEUED,
                    AgentExecutionStatus.PREPARING_BROWSER,
                    AgentExecutionStatus.FAILED)),
            Map.entry(AgentExecutionStatus.RESOLVING_SCOPE, Set.of(
                    AgentExecutionStatus.WAITING_CONFIRMATION,
                    AgentExecutionStatus.QUEUED,
                    AgentExecutionStatus.FAILED)),
            Map.entry(AgentExecutionStatus.WAITING_CONFIRMATION, Set.of(
                    AgentExecutionStatus.QUEUED,
                    AgentExecutionStatus.PREPARING_BROWSER,
                    AgentExecutionStatus.FAILED)),
            Map.entry(AgentExecutionStatus.QUEUED, Set.of(
                    AgentExecutionStatus.PREPARING_BROWSER,
                    AgentExecutionStatus.EXPIRED,
                    AgentExecutionStatus.FAILED)),
            Map.entry(AgentExecutionStatus.PREPARING_BROWSER, Set.of(
                    AgentExecutionStatus.WAITING_LOGIN,
                    AgentExecutionStatus.RUNNING,
                    AgentExecutionStatus.PAUSED,
                    AgentExecutionStatus.FAILED,
                    AgentExecutionStatus.EXPIRED)),
            Map.entry(AgentExecutionStatus.WAITING_LOGIN, Set.of(
                    AgentExecutionStatus.RUNNING,
                    AgentExecutionStatus.PAUSED,
                    AgentExecutionStatus.EXPIRED,
                    AgentExecutionStatus.FAILED)),
            Map.entry(AgentExecutionStatus.RUNNING, Set.of(
                    AgentExecutionStatus.PAUSED,
                    AgentExecutionStatus.WRITING_BACK,
                    AgentExecutionStatus.FAILED,
                    AgentExecutionStatus.EXPIRED)),
            Map.entry(AgentExecutionStatus.PAUSED, Set.of(
                    AgentExecutionStatus.QUEUED,
                    AgentExecutionStatus.PREPARING_BROWSER,
                    AgentExecutionStatus.WAITING_LOGIN,
                    AgentExecutionStatus.RUNNING,
                    AgentExecutionStatus.FAILED,
                    AgentExecutionStatus.EXPIRED)),
            Map.entry(AgentExecutionStatus.WRITING_BACK, Set.of(
                    AgentExecutionStatus.SUCCESS,
                    AgentExecutionStatus.PARTIAL_SUCCESS,
                    AgentExecutionStatus.FAILED))
    );

    private AgentExecutionStateMachine() {
    }

    public static boolean canTransition(String from, String to) {
        if (from == null || to == null || from.equals(to) || AgentExecutionStatus.TERMINAL.contains(from)) {
            return false;
        }
        if (AgentExecutionStatus.CANCELED.equals(to)) {
            return CANCELLABLE.contains(from);
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Illegal AI execution task transition: " + from + " -> " + to);
        }
    }
}
