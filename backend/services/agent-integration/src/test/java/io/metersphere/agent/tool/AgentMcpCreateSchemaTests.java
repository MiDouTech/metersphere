package io.metersphere.agent.tool;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AgentMcpCreateSchemaTests {
    private final BuiltinAgentMcpToolConfig config = new BuiltinAgentMcpToolConfig();
    @Test void publishedCreateLimitsMatchServiceValidation(){
        var single=(Map<?,?>)config.functionalCaseCreateTool(null).inputSchema().get("properties");
        assertEquals(255,((Map<?,?>)single.get("name")).get("maxLength"));
        var batch=(Map<?,?>)config.functionalCaseBatchCreateTool(null).inputSchema().get("properties");
        var cases=(Map<?,?>)batch.get("cases");assertEquals(100,cases.get("maxItems"));assertEquals(1,cases.get("minItems"));
        var item=(Map<?,?>)((Map<?,?>)cases.get("items")).get("properties");assertEquals(255,((Map<?,?>)item.get("name")).get("maxLength"));
    }

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
