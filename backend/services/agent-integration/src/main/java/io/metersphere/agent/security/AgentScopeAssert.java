package io.metersphere.agent.security;

import io.metersphere.agent.constants.AgentErrorCode;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

public final class AgentScopeAssert {

    private AgentScopeAssert() {
    }

    public static void assertScope(String requiredScope) {
        AgentToken token = requireToken();
        if (hasScope(token.getScopes(), requiredScope)) {
            return;
        }
        throw new MSException(AgentErrorCode.SCOPE_DENIED, "Agent token scope 不足: " + requiredScope);
    }

    public static void assertAnyScope(String... requiredScopes) {
        AgentToken token = requireToken();
        if (requiredScopes == null || requiredScopes.length == 0) {
            throw new MSException(AgentErrorCode.SCOPE_DENIED, "Agent token scope 不足: (empty required)");
        }
        for (String requiredScope : requiredScopes) {
            if (hasScope(token.getScopes(), requiredScope)) {
                return;
            }
        }
        throw new MSException(AgentErrorCode.SCOPE_DENIED,
                "Agent token scope 不足: " + String.join("|", requiredScopes));
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
            throw new MSException(AgentErrorCode.SCOPE_DENIED, "Agent token 缺失，无法校验 scope");
        }
        if (StringUtils.isBlank(token.getScopes())) {
            throw new MSException(AgentErrorCode.SCOPE_DENIED, "Agent token scopes 为空");
        }
        return token;
    }
}
