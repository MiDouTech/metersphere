package io.metersphere.agent.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutionModeTests {
    @Test
    void shouldNormalizeModeAndSupportedAgentTypes() {
        assertEquals(AgentExecutionMode.RUNNER, AgentExecutionMode.normalizeMode(null));
        assertEquals(AgentExecutionMode.AGENT, AgentExecutionMode.normalizeMode(" agent "));
        assertEquals("CURSOR", AgentExecutionMode.normalizeAgentType(" cursor "));
        assertNull(AgentExecutionMode.normalizeAgentType(null));
        assertTrue(AgentExecutionMode.SUPPORTED_AGENTS.containsAll(
                java.util.List.of("WORKBUDDY", "CURSOR", "CODEX")));
    }
}
