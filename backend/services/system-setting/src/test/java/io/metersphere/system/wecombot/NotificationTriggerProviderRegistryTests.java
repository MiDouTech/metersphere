package io.metersphere.system.wecombot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationTriggerProviderRegistryTests {
    private final NotificationTriggerProviderRegistry registry = new NotificationTriggerProviderRegistry();

    @Test
    void exposesChineseMetadataForEverySupportedBugVariable() {
        var variables = registry.variables("BUG_EXPECTED_RESOLUTION_DUE");
        assertTrue(variables.stream().anyMatch(variable -> "bugStatus".equals(variable.key())
                && "缺陷状态".equals(variable.name()) && !variable.description().isBlank()
                && !variable.example().isBlank()));
        assertEquals(registry.require("BUG_EXPECTED_RESOLUTION_DUE").variables().size() + 1, variables.size());
        assertTrue(variables.stream().anyMatch(variable -> "ruleName".equals(variable.key())));
    }
}
