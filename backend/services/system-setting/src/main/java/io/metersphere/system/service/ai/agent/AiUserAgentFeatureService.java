package io.metersphere.system.service.ai.agent;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiUserAgentFeatureService {
    @Value("${ms.ai.user-agent.enabled:true}")
    private boolean enabled;
    @Value("${ms.ai.user-agent.workbuddy-enabled:true}")
    private boolean workbuddyEnabled;
    @Value("${ms.ai.user-agent.codex-enabled:true}")
    private boolean codexEnabled;
    @Value("${ms.ai.user-agent.cursor-enabled:true}")
    private boolean cursorEnabled;
    @Value("${ms.ai.user-agent.bridge.windows-download-url:}")
    private String windowsDownloadUrl;
    @Value("${ms.ai.user-agent.bridge.minimum-version:0.1.0}")
    private String minimumBridgeVersion;

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

    public java.util.Map<String, Object> bridgeInstallInfo() {
        return java.util.Map.of(
                "productName", "MeterSphere Agent",
                "windowsDownloadUrl", StringUtils.defaultString(windowsDownloadUrl),
                "minimumVersion", StringUtils.defaultIfBlank(minimumBridgeVersion, "0.1.0"),
                "protocolScheme", "metersphere-agent");
    }
}
