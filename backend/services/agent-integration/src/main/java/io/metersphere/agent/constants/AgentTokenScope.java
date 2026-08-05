package io.metersphere.agent.constants;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class AgentTokenScope {
    public static final String FUNCTIONAL_READ = "FUNCTIONAL_READ";
    public static final String FUNCTIONAL_SUBMIT = "FUNCTIONAL_SUBMIT";
    public static final String FUNCTIONAL_ALL = "FUNCTIONAL_ALL";

    public static final String PROJECT_READ = "PROJECT_READ";
    public static final String PROJECT_WRITE = "PROJECT_WRITE";
    public static final String CASE_WRITE = "CASE_WRITE";
    public static final String CASE_UPDATE = "CASE_UPDATE";
    public static final String CASE_DELETE = "CASE_DELETE";
    public static final String CASE_COMMENT = "CASE_COMMENT";
    public static final String CASE_ATTACHMENT = "CASE_ATTACHMENT";
    public static final String PLAN_READ = "PLAN_READ";
    public static final String PLAN_WRITE = "PLAN_WRITE";
    public static final String AI_EXECUTION_READ = "AI_EXECUTION_READ";
    public static final String AI_EXECUTION_RUN = "AI_EXECUTION_RUN";
    public static final String AI_EXECUTION_CANCEL = "AI_EXECUTION_CANCEL";
    public static final String AI_EXECUTION_LOGIN = "AI_EXECUTION_LOGIN";
    public static final String AI_EXECUTION_ADMIN = "AI_EXECUTION_ADMIN";
    public static final String REVIEW_WRITE = "REVIEW_WRITE";
    public static final String BUG_READ = "BUG_READ";
    public static final String BUG_WRITE = "BUG_WRITE";
    public static final String BUG_DELETE = "BUG_DELETE";
    public static final String BUG_COMMENT = "BUG_COMMENT";
    public static final String BUG_ATTACHMENT = "BUG_ATTACHMENT";
    public static final String BUG_RELATE = "BUG_RELATE";
    public static final String AGENT_ALL = "AGENT_ALL";

    private static final Set<String> KNOWN_SCOPES;

    static {
        Set<String> scopes = new LinkedHashSet<>();
        scopes.add(FUNCTIONAL_READ);
        scopes.add(FUNCTIONAL_SUBMIT);
        scopes.add(FUNCTIONAL_ALL);
        scopes.add(PROJECT_READ);
        scopes.add(PROJECT_WRITE);
        scopes.add(CASE_WRITE);
        scopes.add(CASE_UPDATE);
        scopes.add(CASE_DELETE);
        scopes.add(CASE_COMMENT);
        scopes.add(CASE_ATTACHMENT);
        scopes.add(PLAN_READ);
        scopes.add(PLAN_WRITE);
        scopes.add(AI_EXECUTION_READ);
        scopes.add(AI_EXECUTION_RUN);
        scopes.add(AI_EXECUTION_CANCEL);
        scopes.add(AI_EXECUTION_LOGIN);
        scopes.add(AI_EXECUTION_ADMIN);
        scopes.add(REVIEW_WRITE);
        scopes.add(BUG_READ);
        scopes.add(BUG_WRITE);
        scopes.add(BUG_DELETE);
        scopes.add(BUG_COMMENT);
        scopes.add(BUG_ATTACHMENT);
        scopes.add(BUG_RELATE);
        scopes.add(AGENT_ALL);
        KNOWN_SCOPES = Collections.unmodifiableSet(scopes);
    }

    private AgentTokenScope() {
    }

    public static Set<String> knownScopes() {
        return KNOWN_SCOPES;
    }

    public static boolean isKnownScope(String scope) {
        return scope != null && KNOWN_SCOPES.contains(scope);
    }

    public static boolean isFunctionalScope(String scope) {
        return FUNCTIONAL_READ.equals(scope)
                || FUNCTIONAL_SUBMIT.equals(scope)
                || CASE_WRITE.equals(scope)
                || CASE_UPDATE.equals(scope)
                || CASE_DELETE.equals(scope)
                || CASE_COMMENT.equals(scope)
                || CASE_ATTACHMENT.equals(scope)
                || PLAN_READ.equals(scope)
                || PLAN_WRITE.equals(scope)
                || AI_EXECUTION_READ.equals(scope)
                || AI_EXECUTION_RUN.equals(scope)
                || AI_EXECUTION_CANCEL.equals(scope)
                || AI_EXECUTION_LOGIN.equals(scope)
                || AI_EXECUTION_ADMIN.equals(scope)
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
