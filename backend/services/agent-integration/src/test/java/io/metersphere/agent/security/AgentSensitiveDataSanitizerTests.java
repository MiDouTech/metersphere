package io.metersphere.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSensitiveDataSanitizerTests {
    @Test void nestedJsonStringIsRedactedWithoutChangingItsFieldType() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        String input="[{\"actual\":\"{\\\"password\\\":\\\"synthetic-nested\\\"}\"}]";
        String result=AgentSensitiveDataSanitizer.sanitizeJson(input);
        assertFalse(result.contains("synthetic-nested"));
        assertTrue(mapper.readTree(result).get(0).get("actual").isTextual());
        assertTrue(AgentSensitiveDataSanitizer.containsSensitive(input));
        assertFalse(AgentSensitiveDataSanitizer.containsSensitive("{ \"actual\": \"ordinary\" }"));
    }

    @Test void excessiveNestingIsRejectedInsteadOfClassifiedAsSafe() {
        org.junit.jupiter.api.Assertions.assertThrows(io.metersphere.sdk.exception.MSException.class,
                ()->AgentSensitiveDataSanitizer.containsSensitive("[".repeat(12)+"0"+"]".repeat(12)));
        org.junit.jupiter.api.Assertions.assertThrows(io.metersphere.sdk.exception.MSException.class,
                ()->AgentSensitiveDataSanitizer.containsSensitive("[".repeat(1100)+"0"+"]".repeat(1100)));
    }

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
