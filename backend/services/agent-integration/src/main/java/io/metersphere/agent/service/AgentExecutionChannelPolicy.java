package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentExecutorChannel;
import io.metersphere.agent.constants.AgentTaskOrigin;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class AgentExecutionChannelPolicy {
    public void assertCreatePair(String taskOrigin, String executorChannel) {
        AgentExecutorChannel.requireLegal(taskOrigin, executorChannel);
    }

    public void assertClaimable(AgentExecutionTaskDTO task, String actorType) {
        assertCreatePair(task.getTaskOrigin(), task.getExecutorChannel());
        boolean runner = "RUNNER".equalsIgnoreCase(actorType);
        boolean personalAgent = "MCP_AGENT".equalsIgnoreCase(actorType);
        if ((runner && !AgentExecutorChannel.MODEL_API_RUNNER.equals(task.getExecutorChannel()))
                || (personalAgent && !AgentExecutorChannel.EXTERNAL_MCP_AGENT.equals(task.getExecutorChannel()))
                || (!runner && !personalAgent)) {
            throw new MSException("TASK_CHANNEL_CLAIM_FORBIDDEN");
        }
    }

    public void assertControllable(AgentExecutionTaskDTO task, String actorType, String actorId) {
        assertCreatePair(task.getTaskOrigin(), task.getExecutorChannel());
        if ("MCP_AGENT".equalsIgnoreCase(actorType)
                && (!AgentTaskOrigin.PERSONAL_MCP.equals(task.getTaskOrigin())
                || !StringUtils.equals(actorId, task.getCreateUser()))) {
            throw new MSException("TASK_CONTROL_FORBIDDEN");
        }
        if (!"MCP_AGENT".equalsIgnoreCase(actorType) && !"PLATFORM_USER".equalsIgnoreCase(actorType)
                && !"RUNNER".equalsIgnoreCase(actorType)) {
            throw new MSException("TASK_CONTROL_FORBIDDEN");
        }
    }
}
