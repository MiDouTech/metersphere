package io.metersphere.system.service.ai.agent;

import io.metersphere.system.dto.ai.agent.AiAgentBridgePackageDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;
import io.metersphere.sdk.exception.MSException;

@Service
public class AiUserAgentFeatureService {
    @Resource
    private AiAgentBridgePackageRepository packageRepository;
    @Value("${ms.ai.user-agent.enabled:false}")
    private boolean enabled;
    @Value("${ms.ai.user-agent.workbuddy-enabled:false}")
    private boolean workbuddyEnabled;
    @Value("${ms.ai.user-agent.codex-enabled:false}")
    private boolean codexEnabled;
    @Value("${ms.ai.user-agent.cursor-enabled:false}")
    private boolean cursorEnabled;
    @Value("${ms.ai.user-agent.workbuddy-verified:false}")
    private boolean workbuddyVerified;
    @Value("${ms.ai.user-agent.codex-verified:false}")
    private boolean codexVerified;
    @Value("${ms.ai.user-agent.cursor-verified:false}")
    private boolean cursorVerified;
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
            case "WORKBUDDY" -> false;
            case "CODEX" -> codexEnabled && codexVerified;
            case "CURSOR" -> cursorEnabled && cursorVerified;
            default -> false;
        };
    }

    public java.util.Map<String, Object> flags() {
        return java.util.Map.of(
                "enabled", enabled,
                "workbuddy", providerEnabled("WORKBUDDY"),
                "codex", providerEnabled("CODEX"),
                "cursor", providerEnabled("CURSOR"),
                "providers", java.util.List.of(
                        providerStatus("WORKBUDDY", workbuddyEnabled, false, workbuddyVerified,
                                "Provider adapter is not implemented"),
                        providerStatus("CODEX", codexEnabled, true, codexVerified,
                                "Provider has not passed deployment verification"),
                        providerStatus("CURSOR", cursorEnabled, true, cursorVerified,
                                "Provider has not passed sandbox verification")));
    }

    private java.util.Map<String, Object> providerStatus(String provider, boolean configured, boolean implemented,
                                                          boolean verified, String unavailableReason) {
        boolean available = enabled && configured && implemented && verified;
        return java.util.Map.of("provider", provider, "configured", configured, "implemented", implemented,
                "verified", verified, "available", available, "reason", available ? "" : unavailableReason);
    }

    public java.util.Map<String, Object> bridgeInstallInfo() {
        AiAgentBridgePackageDTO activePackage = packageRepository == null
                ? null : packageRepository.findActive("WINDOWS", "X64");
        return java.util.Map.of(
                "productName", "MeterSphere Agent",
                "windowsDownloadUrl", StringUtils.defaultString(windowsDownloadUrl),
                "managedDownloadAvailable", activePackage != null,
                "managedDownloadPath", activePackage == null ? "" : "/ai/agent-bridge/download?osType=WINDOWS&architecture=X64",
                "publishedVersion", activePackage == null ? "" : activePackage.getVersion(),
                "sha256", activePackage == null ? "" : activePackage.getSha256(),
                "sizeBytes", activePackage == null ? 0L : activePackage.getSizeBytes(),
                "minimumVersion", StringUtils.defaultIfBlank(minimumBridgeVersion, "0.1.0"),
                "protocolScheme", "metersphere-agent");
    }

    public void assertBridgeVersionSupported(String version) {
        if (compareVersions(version, minimumBridgeVersion) < 0) {
            throw new MSException("AGENT_BRIDGE_VERSION_UNSUPPORTED：Bridge 版本不得低于 " + minimumBridgeVersion);
        }
    }

    static int compareVersions(String left, String right) {
        String[] a = StringUtils.defaultString(left).replaceFirst("^[vV]", "").split("[.-]");
        String[] b = StringUtils.defaultString(right).replaceFirst("^[vV]", "").split("[.-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int ai = i < a.length && StringUtils.isNumeric(a[i]) ? Integer.parseInt(a[i]) : 0;
            int bi = i < b.length && StringUtils.isNumeric(b[i]) ? Integer.parseInt(b[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }
}
