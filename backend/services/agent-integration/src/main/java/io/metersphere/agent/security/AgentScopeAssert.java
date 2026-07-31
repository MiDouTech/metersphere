package io.metersphere.agent.security;

import io.metersphere.agent.constants.AgentErrorCode;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import io.metersphere.system.domain.AgentToken;
import org.apache.commons.lang3.StringUtils;

public final class AgentScopeAssert {

    private AgentScopeAssert() {
    }

    public static void assertScope(String requiredScope) {
        AgentToken token = AgentTokenContext.get();
        if (token == null || StringUtils.isBlank(token.getScopes())) {
            return;
        }
        if (hasScope(token.getScopes(), requiredScope)) {
            return;
        }
        throw new MSException(AgentErrorCode.SCOPE_DENIED, "Agent token scope 不足: " + requiredScope);
    }

    public static void assertAnyScope(String... requiredScopes) {
        AgentToken token = AgentTokenContext.get();
        if (token == null || StringUtils.isBlank(token.getScopes()) || requiredScopes == null || requiredScopes.length == 0) {
            return;
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
        if (StringUtils.isBlank(scopes) || StringUtils.isBlank(requiredScope)) {
            return false;
        }
        if (StringUtils.contains(scopes, AgentTokenScope.AGENT_ALL)) {
            return true;
        }
        if (AgentTokenScope.isFunctionalScope(requiredScope)
                && StringUtils.contains(scopes, AgentTokenScope.FUNCTIONAL_ALL)) {
            return true;
        }
        // BUG_WRITE 覆盖 BUG_READ
        if (AgentTokenScope.BUG_READ.equals(requiredScope)
                && StringUtils.contains(scopes, AgentTokenScope.BUG_WRITE)) {
            return true;
        }
        return StringUtils.contains(scopes, requiredScope);
    }
}
