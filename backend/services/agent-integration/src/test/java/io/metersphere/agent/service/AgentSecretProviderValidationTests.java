package io.metersphere.agent.service;

import io.metersphere.agent.secret.EnvironmentSecretProvider;
import io.metersphere.agent.secret.VaultSecretProvider;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentSecretProviderValidationTests {

    @Test
    void environmentReferenceUsesAStableValidationCode() {
        EnvironmentSecretProvider provider = new EnvironmentSecretProvider();
        assertDoesNotThrow(() -> provider.validateReference("env://TEST_ADMIN_SECRET"));
        MSException error = assertThrows(MSException.class, () -> provider.validateReference("secret"));
        assertEquals("CREDENTIAL_SECRET_REF_INVALID", error.getMessage());
    }

    @Test
    void vaultReferenceUsesAStableValidationCodeAndRejectsTraversal() {
        VaultSecretProvider provider = new VaultSecretProvider();
        assertDoesNotThrow(() -> provider.validateReference("vault://secret/test/admin#password"));
        MSException error = assertThrows(MSException.class,
                () -> provider.validateReference("vault://secret/test/../admin#password"));
        assertEquals("CREDENTIAL_SECRET_REF_INVALID", error.getMessage());
    }
}
