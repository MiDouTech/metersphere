package io.metersphere.system.service.ai.agent;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiUserAgentFeatureDeprecationTests {

    @Test
    void publicFeatureFlagsDirectUsersToRemoteMcp() {
        Map<String, Object> flags = new AiUserAgentFeatureService().flags();

        assertFalse((Boolean) flags.get("enabled"));
        assertTrue((Boolean) flags.get("deprecated"));
        assertEquals("REMOTE_MCP", flags.get("migrationTarget"));
    }

    @Test
    void rejectsNewBridgeProvisioning() {
        assertThrows(MSException.class, new AiUserAgentFeatureService()::assertProvisioningAllowed);
    }
}
