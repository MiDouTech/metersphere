package io.metersphere.agent.tool;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AgentMcpCreateSchemaTests {
    private final BuiltinAgentMcpToolConfig config = new BuiltinAgentMcpToolConfig();

    @Test void nativeCreateSchemasExposeRequiredIdempotencyKey() {
        for (var tool : List.of(config.projectCreateTool(null), config.functionalCaseCreateTool(null))) {
            var schema = tool.inputSchema();
            assertTrue(((Map<?, ?>) schema.get("properties")).containsKey("requestId"));
            assertTrue(((List<?>) schema.get("required")).contains("requestId"));
            assertEquals(false, schema.get("additionalProperties"));
        }
    }

    @Test void bugCustomFieldsDescribeStringValuesAndRejectArrayBeforeService() {
        var tool = config.bugCreateTool(null);
        var fields = (Map<?, ?>) ((Map<?, ?>) tool.inputSchema().get("properties")).get("customFields");
        assertEquals("string", ((Map<?, ?>) fields.get("additionalProperties")).get("type"));
        var error = assertThrows(MSException.class, () -> tool.execute(Map.of(
                "projectId", "p", "title", "audit", "requestId", "r",
                "customFields", Map.of("handleUser", List.of("u")))));
        assertEquals("AGENT_VALIDATION_ERROR", error.getMessage());
    }
}
