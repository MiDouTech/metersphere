package io.metersphere.system.wecombot;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class NotificationTriggerProviderRegistry {
    public record Provider(String notificationType, String triggerType, List<String> scopes, List<String> variables) {
    }

    private final Map<String, Provider> providers = Map.of(
            "BUG_EXPECTED_RESOLUTION_DUE", new Provider("BUG_EXPECTED_RESOLUTION_DUE", "DEADLINE",
                    List.of("SYSTEM", "PROJECT"), List.of("bugNum", "bugTitle", "bugHandlerNames", "bugCreatorName", "expectedResolveTime", "remainingTime", "projectName", "resourceUrl")),
            "TEST_REPORT_GENERATED", new Provider("TEST_REPORT_GENERATED", "EVENT",
                    List.of("SYSTEM", "PROJECT"), List.of("projectName", "testPlanName", "reportName", "reportGeneratorName", "reportSummary", "reportUrl", "generatedAt")),
            "CUSTOM_CRON", new Provider("CUSTOM_CRON", "CRON",
                    List.of("SYSTEM", "PROJECT"), List.of("customTitle", "customContent", "now"))
    );

    public Provider require(String type) {
        Provider provider = providers.get(type);
        if (provider == null) throw new io.metersphere.sdk.exception.MSException("Unsupported notification type");
        return provider;
    }

    public List<Provider> list() { return List.copyOf(providers.values()); }
}
