package io.metersphere.agent.security;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AgentScopeAssertTests {

    @AfterEach
    void tearDown() {
        AgentTokenContext.clear();
    }

    @Test
    void agentAllShouldAllowAnyScope() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.AGENT_ALL);
        AgentTokenContext.set(token);
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.PROJECT_WRITE));
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.BUG_WRITE));
    }

    @Test
    void functionalAllShouldNotGrantProjectWrite() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.FUNCTIONAL_ALL);
        AgentTokenContext.set(token);
        MSException ex = Assertions.assertThrows(MSException.class,
                () -> AgentScopeAssert.assertScope(AgentTokenScope.PROJECT_WRITE));
        Assertions.assertTrue(ex.getMessage().contains(AgentTokenScope.PROJECT_WRITE));
    }

    @Test
    void functionalAllShouldGrantSubmit() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.FUNCTIONAL_ALL);
        AgentTokenContext.set(token);
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_SUBMIT));
    }

    @Test
    void caseWriteShouldNotGrantProjectWrite() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.CASE_WRITE);
        AgentTokenContext.set(token);
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.CASE_WRITE));
        MSException ex = Assertions.assertThrows(MSException.class,
                () -> AgentScopeAssert.assertScope(AgentTokenScope.PROJECT_WRITE));
        Assertions.assertTrue(ex.getMessage().contains(AgentTokenScope.PROJECT_WRITE));
    }
}
