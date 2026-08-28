package io.metersphere.agent.secret;

public interface AgentSecretProvider {
    String type();
    void validateReference(String secretRef);
    SecretMetadata verify(String secretRef);
    ResolvedSecret resolve(String secretRef, String usernameHint, SecretResolveContext context);
    default void revokeLease(SecretResolveContext context) { }

    record SecretMetadata(String version, Long expiresAt) {}
}
