package io.metersphere.agent.dto;

public record AgentCredentialResolveResponse(String algorithm, String encryptedKey, String iv,
                                             String encryptedPayload, String secretVersion,
                                             Long expiresAt, String traceId) { }
