package io.metersphere.agent.tool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class AgentMcpToolRegistryTests {

    @Test
    void aliasSearchProjectsShouldResolveCanonicalTool() {
        AgentMcpToolHandler handler = new AgentMcpToolHandler() {
            @Override
            public String name() {
                return "metersphere.project.search";
            }

            @Override
            public String requiredScope() {
                return "PROJECT_READ";
            }

            @Override
            public Map<String, Object> inputSchema() {
                return Map.of();
            }

            @Override
            public Map<String, Object> annotations() {
                return Map.of("readOnlyHint", true);
            }

            @Override
            public Object execute(Map<String, Object> arguments) {
                return Map.of("ok", true);
            }
        };
        AgentMcpToolRegistry registry = new AgentMcpToolRegistry(List.of(handler));
        Assertions.assertTrue(registry.find("search_projects").isPresent());
        Assertions.assertEquals("metersphere.project.search", registry.find("search_projects").get().name());
        Assertions.assertTrue(registry.isWriteTool("metersphere.project.create") == false
                || registry.find("metersphere.project.create").isEmpty());
        Assertions.assertFalse(registry.isWriteTool("metersphere.project.search"));
    }
}
