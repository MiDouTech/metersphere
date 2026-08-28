package io.metersphere.agent.service;
public interface AgentTestDataCleanupHandler {boolean supports(String cleanupType);void cleanup(String leaseId,String taskId,String executionId,String datasetId,String dataKey,String namespace);}
