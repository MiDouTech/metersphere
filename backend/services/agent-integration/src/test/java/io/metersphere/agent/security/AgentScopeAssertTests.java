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
    void nullTokenShouldDeny() {
        AgentTokenContext.clear();
        Assertions.assertThrows(MSException.class, () -> AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ));
    }

    @Test
    void blankScopesShouldDeny() {
        AgentToken token = new AgentToken();
        token.setScopes("   ");
        AgentTokenContext.set(token);
        Assertions.assertThrows(MSException.class, () -> AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ));
    }

    @Test
    void emptyScopesShouldDeny() {
        AgentToken token = new AgentToken();
        token.setScopes("");
        AgentTokenContext.set(token);
        Assertions.assertThrows(MSException.class, () -> AgentScopeAssert.assertScope(AgentTokenScope.BUG_WRITE));
    }

    @Test
    void substringLookalikeShouldNotGrantAgentAll() {
        AgentToken token = new AgentToken();
        token.setScopes("XAGENT_ALL");
        AgentTokenContext.set(token);
        Assertions.assertThrows(MSException.class, () -> AgentScopeAssert.assertScope(AgentTokenScope.PROJECT_WRITE));
    }

    @Test
    void substringLookalikeShouldNotGrantBugWrite() {
        AgentToken token = new AgentToken();
        token.setScopes("BUG_WRITE_EXT");
        AgentTokenContext.set(token);
        Assertions.assertThrows(MSException.class, () -> AgentScopeAssert.assertScope(AgentTokenScope.BUG_WRITE));
        Assertions.assertThrows(MSException.class, () -> AgentScopeAssert.assertScope(AgentTokenScope.BUG_READ));
    }

    @Test
    void agentAllShouldAllowAnyScope() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.AGENT_ALL);
        AgentTokenContext.set(token);
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.PROJECT_WRITE));
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.BUG_WRITE));
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.PROJECT_READ));
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
    void functionalReadShouldTemporarilyGrantProjectRead() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.FUNCTIONAL_READ);
        AgentTokenContext.set(token);
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.PROJECT_READ));
    }

    @Test
    void projectReadAloneShouldGrantProjectRead() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.PROJECT_READ);
        AgentTokenContext.set(token);
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.PROJECT_READ));
        Assertions.assertThrows(MSException.class, () -> AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ));
    }

    @Test
    void functionalAllShouldGrantSubmit() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.FUNCTIONAL_ALL);
        AgentTokenContext.set(token);
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_SUBMIT));
    }

    @Test
    void functionalAllShouldGrantFunctionalWriteScopes() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.FUNCTIONAL_ALL);
        AgentTokenContext.set(token);
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.CASE_WRITE));
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.CASE_UPDATE));
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.CASE_COMMENT));
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.CASE_ATTACHMENT));
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.CASE_DELETE));
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.PLAN_WRITE));
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.REVIEW_WRITE));
    }

    @Test
    void bugWriteShouldOnlyGrantBugReadCompatibility() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.BUG_WRITE);
        AgentTokenContext.set(token);
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.BUG_READ));
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.BUG_WRITE));
        Assertions.assertThrows(MSException.class, () -> AgentScopeAssert.assertScope(AgentTokenScope.BUG_COMMENT));
        Assertions.assertThrows(MSException.class, () -> AgentScopeAssert.assertScope(AgentTokenScope.BUG_ATTACHMENT));
        Assertions.assertThrows(MSException.class, () -> AgentScopeAssert.assertScope(AgentTokenScope.BUG_RELATE));
        Assertions.assertThrows(MSException.class, () -> AgentScopeAssert.assertScope(AgentTokenScope.BUG_DELETE));
    }

    @Test
    void multiScopeExactCombination() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.FUNCTIONAL_READ + ";" + AgentTokenScope.BUG_WRITE);
        AgentTokenContext.set(token);
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ));
        Assertions.assertDoesNotThrow(() -> AgentScopeAssert.assertScope(AgentTokenScope.BUG_READ));
        Assertions.assertThrows(MSException.class, () -> AgentScopeAssert.assertScope(AgentTokenScope.CASE_WRITE));
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

    @Test
    void missingWriteScopeShouldDenyAssertAny() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.FUNCTIONAL_READ);
        AgentTokenContext.set(token);
        Assertions.assertThrows(MSException.class,
                () -> AgentScopeAssert.assertAnyScope(AgentTokenScope.CASE_WRITE, AgentTokenScope.BUG_WRITE));
    }
}
