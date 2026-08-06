package io.metersphere.agent.constants;

import java.util.List;

public final class AgentExecutionStatus {
    public static final String CREATED = "CREATED";
    public static final String RESOLVING_SCOPE = "RESOLVING_SCOPE";
    public static final String WAITING_CONFIRMATION = "WAITING_CONFIRMATION";
    public static final String PREPARING_BROWSER = "PREPARING_BROWSER";
    public static final String WAITING_LOGIN = "WAITING_LOGIN";
    public static final String RUNNING = "RUNNING";
    public static final String PAUSED = "PAUSED";
    public static final String WRITING_BACK = "WRITING_BACK";
    public static final String SUCCESS = "SUCCESS";
    public static final String PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String CANCELED = "CANCELED";

    public static final String CASE_BLOCKED = "BLOCKED";
    public static final String CASE_SKIPPED = "SKIPPED";

    public static final List<String> TERMINAL = List.of(SUCCESS, PARTIAL_SUCCESS, FAILED, CANCELED);
    public static final List<String> HOLDING = List.of(
            RESOLVING_SCOPE, WAITING_CONFIRMATION, PREPARING_BROWSER, WAITING_LOGIN, PAUSED);

    private AgentExecutionStatus() {
    }
}
