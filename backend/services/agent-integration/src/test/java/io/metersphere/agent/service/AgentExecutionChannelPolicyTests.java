package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentExecutorChannel;
import io.metersphere.agent.constants.AgentTaskOrigin;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentExecutionChannelPolicyTests {
    private final AgentExecutionChannelPolicy policy = new AgentExecutionChannelPolicy();

    @Test void acceptsOnlyTheThreeSupportedCreatePairs() {
        assertDoesNotThrow(() -> policy.assertCreatePair(AgentTaskOrigin.PERSONAL_MCP, AgentExecutorChannel.EXTERNAL_MCP_AGENT));
        assertDoesNotThrow(() -> policy.assertCreatePair(AgentTaskOrigin.PLATFORM_MANUAL, AgentExecutorChannel.MODEL_API_RUNNER));
        assertDoesNotThrow(() -> policy.assertCreatePair(AgentTaskOrigin.PLATFORM_SCHEDULED, AgentExecutorChannel.MODEL_API_RUNNER));
        assertThrows(MSException.class, () -> policy.assertCreatePair(AgentTaskOrigin.PERSONAL_MCP, AgentExecutorChannel.MODEL_API_RUNNER));
        assertThrows(MSException.class, () -> policy.assertCreatePair(AgentTaskOrigin.PLATFORM_MANUAL, AgentExecutorChannel.EXTERNAL_MCP_AGENT));
        assertThrows(MSException.class, () -> policy.assertCreatePair(AgentTaskOrigin.PLATFORM_SCHEDULED, AgentExecutorChannel.EXTERNAL_MCP_AGENT));
    }

    @Test void personalAgentCannotClaimPlatformTask() {
        AgentExecutionTaskDTO task=new AgentExecutionTaskDTO();task.setTaskOrigin(AgentTaskOrigin.PLATFORM_SCHEDULED);task.setExecutorChannel(AgentExecutorChannel.MODEL_API_RUNNER);
        assertThrows(MSException.class,()->policy.assertClaimable(task,"MCP_AGENT"));
        assertDoesNotThrow(()->policy.assertClaimable(task,"RUNNER"));
    }
}
