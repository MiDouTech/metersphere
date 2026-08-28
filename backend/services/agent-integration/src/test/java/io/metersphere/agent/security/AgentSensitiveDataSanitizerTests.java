package io.metersphere.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSensitiveDataSanitizerTests {

    @Test
    void redactsCommonExecutorSecretsBeforePersistence() {
        String sanitized = AgentSensitiveDataSanitizer.sanitize(
                "Authorization: Bearer abc.def token=secret-value api_key=top-secret password=pwd");

        assertTrue(sanitized.contains("***"));
        assertFalse(sanitized.contains("abc.def"));
        assertFalse(sanitized.contains("secret-value"));
        assertFalse(sanitized.contains("top-secret"));
        assertFalse(sanitized.contains("password=pwd"));
    }
}
