package io.metersphere.agent.constants;

import java.util.Set;

/**
 * Business outcome of a completed execution. This must never be inferred from
 * the orchestration status alone: a technically successful run may still find
 * a product defect.
 */
public final class AgentExecutionVerdict {
    public static final String PASSED = "PASSED";
    public static final String PRODUCT_FAILED = "PRODUCT_FAILED";
    public static final String ENV_FAILED = "ENV_FAILED";
    public static final String DATA_FAILED = "DATA_FAILED";
    public static final String AGENT_FAILED = "AGENT_FAILED";
    public static final String BLOCKED = "BLOCKED";
    public static final String INCONCLUSIVE = "INCONCLUSIVE";

    public static final Set<String> ALL = Set.of(PASSED, PRODUCT_FAILED, ENV_FAILED, DATA_FAILED,
            AGENT_FAILED, BLOCKED, INCONCLUSIVE);

    private AgentExecutionVerdict() {
    }
}
