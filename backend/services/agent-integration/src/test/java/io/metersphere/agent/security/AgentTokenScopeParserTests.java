package io.metersphere.agent.security;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class AgentTokenScopeParserTests {

    @Test
    void parseShouldSplitAndDeduplicate() {
        Set<String> scopes = AgentTokenScopeParser.parse("functional_read; BUG_WRITE,BUG_WRITE  PROJECT_READ");
        Assertions.assertEquals(Set.of("FUNCTIONAL_READ", "BUG_WRITE", "PROJECT_READ"), scopes);
    }

    @Test
    void parseJsonArrayLiteral() {
        Set<String> scopes = AgentTokenScopeParser.parse("[\"AGENT_ALL\",\"PROJECT_READ\"]");
        Assertions.assertTrue(scopes.contains(AgentTokenScope.AGENT_ALL));
        Assertions.assertTrue(scopes.contains(AgentTokenScope.PROJECT_READ));
    }

    @Test
    void normalizeShouldRejectUnknown() {
        Assertions.assertThrows(MSException.class, () -> AgentTokenScopeParser.normalizeAndValidate("NOT_A_REAL_SCOPE"));
    }

    @Test
    void normalizeShouldRejectBlank() {
        Assertions.assertThrows(MSException.class, () -> AgentTokenScopeParser.normalizeAndValidate("  "));
    }

    @Test
    void normalizeShouldJoinKnownScopes() {
        String normalized = AgentTokenScopeParser.normalizeAndValidate("project_read;functional_read");
        Assertions.assertEquals("PROJECT_READ;FUNCTIONAL_READ", normalized);
    }

    @Test
    void hasScopeShouldNotSubstringMatch() {
        Assertions.assertFalse(AgentTokenScopeParser.hasScope("XAGENT_ALL", AgentTokenScope.PROJECT_WRITE));
        Assertions.assertFalse(AgentTokenScopeParser.hasScope("BUG_WRITE_EXT", AgentTokenScope.BUG_WRITE));
        Assertions.assertTrue(AgentTokenScopeParser.hasScope(AgentTokenScope.AGENT_ALL, AgentTokenScope.BUG_COMMENT));
    }
}
