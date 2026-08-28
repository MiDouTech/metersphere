package io.metersphere.agent.secret;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentSecretProviderTests {
    @Test
    void registryRejectsUnknownProvider() {
        AgentSecretProvider provider = new AgentSecretProvider() {
            public String type() { return "ENV"; }
            public void validateReference(String secretRef) { }
            public SecretMetadata verify(String secretRef) { return new SecretMetadata("v1", null); }
            public ResolvedSecret resolve(String secretRef, String usernameHint, SecretResolveContext context) { throw new UnsupportedOperationException(); }
        };
        AgentSecretProviderRegistry registry = new AgentSecretProviderRegistry(List.of(provider));
        assertEquals(provider, registry.require("env"));
        assertThrows(MSException.class, () -> registry.require("vault"));
    }

    @Test
    void environmentReferenceUsesStrictVariableSyntax() {
        EnvironmentSecretProvider provider = new EnvironmentSecretProvider();
        provider.validateReference("env://TEST_ADMIN_SECRET");
        assertThrows(MSException.class, () -> provider.validateReference("env://lower-case"));
        assertThrows(MSException.class, () -> provider.validateReference("file:///tmp/secret"));
        assertThrows(MSException.class, () -> provider.validateReference("env://A"));
    }

    @Test
    void vaultReferenceUsesKvv2PathAndFieldWithoutTraversal() {
        VaultSecretProvider provider = new VaultSecretProvider();
        provider.validateReference("vault://qa/apps/test-admin#password");
        assertThrows(MSException.class, () -> provider.validateReference("vault://qa/../root#password"));
        assertThrows(MSException.class, () -> provider.validateReference("http://vault/secret"));
    }
}
