package io.metersphere.agent.constants;

public class AgentTokenScope {
    public static final String FUNCTIONAL_READ = "FUNCTIONAL_READ";
    public static final String FUNCTIONAL_SUBMIT = "FUNCTIONAL_SUBMIT";
    public static final String FUNCTIONAL_ALL = "FUNCTIONAL_ALL";

    public static final String PROJECT_WRITE = "PROJECT_WRITE";
    public static final String CASE_WRITE = "CASE_WRITE";
    public static final String CASE_UPDATE = "CASE_UPDATE";
    public static final String CASE_DELETE = "CASE_DELETE";
    public static final String CASE_COMMENT = "CASE_COMMENT";
    public static final String CASE_ATTACHMENT = "CASE_ATTACHMENT";
    public static final String PLAN_WRITE = "PLAN_WRITE";
    public static final String REVIEW_WRITE = "REVIEW_WRITE";
    public static final String BUG_READ = "BUG_READ";
    public static final String BUG_WRITE = "BUG_WRITE";
    public static final String BUG_DELETE = "BUG_DELETE";
    public static final String BUG_COMMENT = "BUG_COMMENT";
    public static final String BUG_ATTACHMENT = "BUG_ATTACHMENT";
    public static final String BUG_RELATE = "BUG_RELATE";
    public static final String AGENT_ALL = "AGENT_ALL";

    private AgentTokenScope() {
    }

    public static boolean isFunctionalScope(String scope) {
        return FUNCTIONAL_READ.equals(scope)
                || FUNCTIONAL_SUBMIT.equals(scope)
                || CASE_WRITE.equals(scope)
                || CASE_UPDATE.equals(scope)
                || CASE_DELETE.equals(scope)
                || CASE_COMMENT.equals(scope)
                || CASE_ATTACHMENT.equals(scope)
                || PLAN_WRITE.equals(scope)
                || REVIEW_WRITE.equals(scope);
    }

    public static boolean isBugScope(String scope) {
        return BUG_READ.equals(scope)
                || BUG_WRITE.equals(scope)
                || BUG_DELETE.equals(scope)
                || BUG_COMMENT.equals(scope)
                || BUG_ATTACHMENT.equals(scope)
                || BUG_RELATE.equals(scope);
    }
}
