package io.metersphere.agent.security;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;

import java.util.Set;

/** Legacy method names retained for callers; these only require an authenticated identity. */
public final class AgentScopeAssert {

    private AgentScopeAssert() {
    }

    public static void assertScope(String requiredScope) {
        AgentToken token = requireToken();
        if (hasScope(token.getScopes(), requiredScope)) {
            return;
        }
        throw new MSException("AGENT_VALIDATION_ERROR");
    }

    public static void assertAnyScope(String... requiredScopes) {
        AgentToken token = requireToken();
        if (requiredScopes == null || requiredScopes.length == 0) {
            throw new MSException("AGENT_VALIDATION_ERROR");
        }
        for (String requiredScope : requiredScopes) {
            if (hasScope(token.getScopes(), requiredScope)) {
                return;
            }
        }
        throw new MSException("AGENT_VALIDATION_ERROR");
    }

    public static boolean hasScope(String scopes, String requiredScope) {
        return AgentTokenScopeParser.hasScope(scopes, requiredScope);
    }

    public static boolean hasScope(Set<String> scopes, String requiredScope) {
        return AgentTokenScopeParser.hasScope(scopes, requiredScope);
    }

    private static AgentToken requireToken() {
        AgentToken token = AgentTokenContext.get();
        if (token == null) {
            throw new MSException("AUTHENTICATION_REQUIRED");
        }
        return token;
    }
}
