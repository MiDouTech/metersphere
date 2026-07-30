package io.metersphere.agent.security;

import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class AgentTokenProjectAccessTests {

    @Test
    void emptyProjectIdsMeansAll() {
        AgentToken token = new AgentToken();
        Assertions.assertTrue(AgentTokenProjectAccess.allowsAll(token));
        Assertions.assertTrue(AgentTokenProjectAccess.allows(token, "any-project"));
    }

    @Test
    void jsonWhitelistRestrictsAccess() {
        AgentToken token = new AgentToken();
        token.setProjectIds("[\"p1\",\"p2\"]");
        Assertions.assertFalse(AgentTokenProjectAccess.allowsAll(token));
        Assertions.assertTrue(AgentTokenProjectAccess.allows(token, "p1"));
        Assertions.assertFalse(AgentTokenProjectAccess.allows(token, "p3"));
        Assertions.assertEquals(List.of("p1", "p2"), AgentTokenProjectAccess.parseProjectIds(token));
    }

    @Test
    void legacyProjectIdFallback() {
        AgentToken token = new AgentToken();
        token.setProjectId("legacy-p");
        Assertions.assertEquals(List.of("legacy-p"), AgentTokenProjectAccess.parseProjectIds(token));
        Assertions.assertTrue(AgentTokenProjectAccess.allows(token, "legacy-p"));
    }

    @Test
    void toStorageJsonNullWhenEmpty() {
        Assertions.assertNull(AgentTokenProjectAccess.toStorageJson(null));
        Assertions.assertNull(AgentTokenProjectAccess.toStorageJson(List.of()));
        Assertions.assertEquals("[\"a\"]", AgentTokenProjectAccess.toStorageJson(List.of("a")));
    }
}
