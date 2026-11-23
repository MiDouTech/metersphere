package io.metersphere.agent.security;

import io.metersphere.sdk.exception.MSException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentTokenScopeParserTests {
    @Test void legacyMetadataStillParsesExactly() {
        assertEquals(Set.of("FUNCTIONAL_READ", "BUG_WRITE", "PROJECT_READ"),
                AgentTokenScopeParser.parse("functional_read; BUG_WRITE,BUG_WRITE PROJECT_READ"));
        assertEquals(Set.of("AGENT_ALL", "PROJECT_READ"),
                AgentTokenScopeParser.parse("[\"AGENT_ALL\",\"PROJECT_READ\"]"));
    }
    @Test void legacyNormalizerRemainsAvailableForStoredMetadata() {
        assertEquals("PROJECT_READ;FUNCTIONAL_READ", AgentTokenScopeParser.normalizeAndValidate("project_read;functional_read"));
        assertThrows(MSException.class, () -> AgentTokenScopeParser.normalizeAndValidate("NOT_A_REAL_SCOPE"));
        assertThrows(MSException.class, () -> AgentTokenScopeParser.normalizeAndValidate(" "));
    }
    @Test void noIndependentFeatureScopeIsRequiredForAnyHistoricalToken() {
        for (String stored : new String[]{null, "", "AGENT_ALL", "FUNCTIONAL_READ", "BUG_WRITE"})
            for (String operation : Set.of("SHARED_CONFIGURATION_RESOLVE", "BUSINESS_ASSET_WRITE", "CASE_IMPORT_V2",
                    "CASE_DELETE_V2", "PROJECT_FILE_DOWNLOAD", "FUTURE_OPERATION"))
                assertTrue(AgentTokenScopeParser.hasScope(stored, operation));
        assertFalse(AgentTokenScopeParser.hasScope("AGENT_ALL", " "));
    }
}
