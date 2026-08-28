package io.metersphere.agent.constants;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentDualChannelContractTests {

    @Test
    void acceptsOnlyTheThreeDefinedOriginChannelPairs() {
        assertDoesNotThrow(() -> AgentExecutorChannel.requireLegal(
                AgentTaskOrigin.PLATFORM_SCHEDULED, AgentExecutorChannel.MODEL_API_RUNNER));
        assertDoesNotThrow(() -> AgentExecutorChannel.requireLegal(
                AgentTaskOrigin.PLATFORM_MANUAL, AgentExecutorChannel.MODEL_API_RUNNER));
        assertDoesNotThrow(() -> AgentExecutorChannel.requireLegal(
                AgentTaskOrigin.PERSONAL_MCP, AgentExecutorChannel.EXTERNAL_MCP_AGENT));

        assertThrows(MSException.class, () -> AgentExecutorChannel.requireLegal(
                AgentTaskOrigin.PLATFORM_SCHEDULED, AgentExecutorChannel.EXTERNAL_MCP_AGENT));
        assertThrows(MSException.class, () -> AgentExecutorChannel.requireLegal(
                AgentTaskOrigin.PLATFORM_MANUAL, AgentExecutorChannel.EXTERNAL_MCP_AGENT));
        assertThrows(MSException.class, () -> AgentExecutorChannel.requireLegal(
                AgentTaskOrigin.PERSONAL_MCP, AgentExecutorChannel.MODEL_API_RUNNER));
    }
}
