package io.metersphere.agent.service;

public record AgentExecutionDispatchEvent(String taskId, String projectId, String gatewayId,
                                          String agentType, String userId) {
}
