package io.metersphere.system.service.ai.agent;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiUserAgentFeatureService {
    @Value("${ms.ai.user-agent.enabled:false}")
    private boolean enabled;
    @Value("${ms.ai.user-agent.workbuddy-enabled:false}")
    private boolean workbuddyEnabled;
    @Value("${ms.ai.user-agent.codex-enabled:false}")
    private boolean codexEnabled;
    @Value("${ms.ai.user-agent.cursor-enabled:false}")
    private boolean cursorEnabled;

    public boolean enabled() {
        return enabled;
    }

    public boolean providerEnabled(String provider) {
        if (!enabled || StringUtils.isBlank(provider)) {
            return false;
        }
        return switch (StringUtils.upperCase(provider)) {
            case "WORKBUDDY" -> workbuddyEnabled;
            case "CODEX" -> codexEnabled;
            case "CURSOR" -> cursorEnabled;
            default -> false;
        };
    }

    public java.util.Map<String, Boolean> flags() {
        return java.util.Map.of("enabled", enabled, "workbuddy", enabled && workbuddyEnabled,
                "codex", enabled && codexEnabled, "cursor", enabled && cursorEnabled);
    }
}
