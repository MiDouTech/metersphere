package io.metersphere.agent.constants;

public class AgentTokenScope {
    public static final String FUNCTIONAL_READ = "FUNCTIONAL_READ";
    public static final String FUNCTIONAL_SUBMIT = "FUNCTIONAL_SUBMIT";
    public static final String FUNCTIONAL_ALL = "FUNCTIONAL_ALL";

    public static final String PROJECT_WRITE = "PROJECT_WRITE";
    public static final String CASE_WRITE = "CASE_WRITE";
    public static final String PLAN_WRITE = "PLAN_WRITE";
    public static final String REVIEW_WRITE = "REVIEW_WRITE";
    public static final String BUG_WRITE = "BUG_WRITE";
    public static final String AGENT_ALL = "AGENT_ALL";

    private AgentTokenScope() {
    }

    public static boolean isFunctionalScope(String scope) {
        return FUNCTIONAL_READ.equals(scope) || FUNCTIONAL_SUBMIT.equals(scope);
    }
}
