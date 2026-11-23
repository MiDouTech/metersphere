package io.metersphere.agent.security;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentScopeAssertTests {
    @AfterEach void clear() { AgentTokenContext.clear(); }
    @Test void absentIdentityIsDenied() {
        assertThrows(MSException.class, () -> AgentScopeAssert.assertScope("SHARED_CONFIGURATION_RESOLVE"));
    }
    @Test void legacyMetadataDoesNotRestrictAuthenticatedIdentity() {
        for (String scopes : new String[]{null, "", "FUNCTIONAL_READ", "BUG_WRITE", "AGENT_ALL"}) {
            AgentToken token = new AgentToken(); token.setId("token"); token.setUserId("actor"); token.setScopes(scopes);
            AgentTokenContext.set(token);
            assertDoesNotThrow(() -> AgentScopeAssert.assertScope("CASE_IMPORT_V2"));
            assertDoesNotThrow(() -> AgentScopeAssert.assertAnyScope("PROJECT_FILE_DOWNLOAD", "CASE_DELETE_V2"));
        }
    }
    @Test void missingOperationMetadataFailsClosed() {
        AgentTokenContext.set(new AgentToken());
        assertThrows(MSException.class, () -> AgentScopeAssert.assertScope(""));
        assertThrows(MSException.class, AgentScopeAssert::assertAnyScope);
    }
}
