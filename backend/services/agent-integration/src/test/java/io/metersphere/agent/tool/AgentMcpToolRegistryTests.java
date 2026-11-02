package io.metersphere.agent.tool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class AgentMcpToolRegistryTests {

    private AgentMcpToolHandler tool(String name) {
        return new AgentMcpToolHandler() {
            public String name() { return name; }
            public String requiredScope() { return "PROJECT_READ"; }
            public Map<String, Object> inputSchema() { return Map.of(); }
            public Map<String, Object> annotations() { return Map.of("readOnlyHint", true); }
            public Object execute(Map<String, Object> arguments) { return Map.of(); }
        };
    }

    @Test void duplicateNamesAndAliasConflictsFailWithoutReplacingOriginal() {
        var original = tool("metersphere.project.search");
        var registry = new AgentMcpToolRegistry(List.of(original));
        Assertions.assertThrows(IllegalStateException.class, () -> registry.register(tool(original.name())));
        Assertions.assertSame(original, registry.find(original.name()).orElseThrow());
        Assertions.assertThrows(IllegalStateException.class, () -> registry.register(tool("search_projects")));
        Assertions.assertThrows(IllegalStateException.class, () -> registry.registerAlias("alias", "missing"));
        Assertions.assertThrows(IllegalStateException.class, () -> registry.registerAlias("search_projects", original.name()));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
    }

    @Test void defaultResultSchemaHasNoOverlappingIntegerBranch() {
        Map<?, ?> result = (Map<?, ?>) ((Map<?, ?>) tool("test").outputSchema().get("properties")).get("result");
        var types = (List<?>) result.get("oneOf");
        Assertions.assertTrue(types.contains(Map.of("type", "number")));
        Assertions.assertFalse(types.contains(Map.of("type", "integer")));
    }

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
