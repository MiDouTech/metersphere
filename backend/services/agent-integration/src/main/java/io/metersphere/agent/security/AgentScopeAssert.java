package io.metersphere.agent.security;

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
        throw new MSException(MsHttpResultCode.FORBIDDEN, "Agent token scope 不足: " + requiredScope);
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
        return StringUtils.contains(scopes, requiredScope);
    }
}
