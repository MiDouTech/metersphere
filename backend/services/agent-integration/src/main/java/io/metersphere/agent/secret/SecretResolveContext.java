package io.metersphere.agent.secret;

public record SecretResolveContext(String taskId, String executionId, String projectId, String environmentId,
                                   String credentialReferenceId, String purpose, String traceId) { }
