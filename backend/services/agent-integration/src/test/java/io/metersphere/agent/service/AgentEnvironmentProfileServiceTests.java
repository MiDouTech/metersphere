package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentEnvironmentProfileDTO;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentEnvironmentProfileServiceTests {
    private final AgentEnvironmentProfileService service = new AgentEnvironmentProfileService();

    @Test
    void shouldAcceptOnlyConfiguredOrigin() {
        AgentEnvironmentProfileDTO profile = profile("https://test.example.com");
        assertDoesNotThrow(() -> service.assertTargetAllowed(profile, "https://test.example.com/users?id=1"));
        assertThrows(MSException.class, () -> service.assertTargetAllowed(profile, "https://evil.example.com"));
    }

    @Test
    void shouldRejectNonHttpAndMetadataTargets() {
        AgentEnvironmentProfileDTO profile = profile("https://test.example.com");
        assertThrows(MSException.class, () -> service.assertTargetAllowed(profile, "file:///etc/passwd"));
        assertThrows(MSException.class, () -> service.assertTargetAllowed(profile, "http://169.254.169.254/latest/meta-data"));
        assertThrows(MSException.class, () -> service.assertTargetAllowed(profile, "http://localhost:8080"));
    }

    private AgentEnvironmentProfileDTO profile(String origin) {
        AgentEnvironmentProfileDTO profile = new AgentEnvironmentProfileDTO();
        profile.setAllowedOrigins(List.of(origin));
        return profile;
    }
}
