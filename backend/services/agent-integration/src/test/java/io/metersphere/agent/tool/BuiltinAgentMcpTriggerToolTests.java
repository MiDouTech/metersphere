package io.metersphere.agent.tool;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentTaskTriggerService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class BuiltinAgentMcpTriggerToolTests {

    private final BuiltinAgentMcpToolConfig config = new BuiltinAgentMcpToolConfig();
    private final AgentTaskTriggerService service = mock(AgentTaskTriggerService.class);

    @Test
    void triggerToolsExposeReadAndWriteScopesConsistently() {
        AgentMcpToolHandler create = config.executionTriggerCreateTool(service);
        AgentMcpToolHandler update = config.executionTriggerUpdateTool(service);
        AgentMcpToolHandler list = config.executionTriggerListTool(service);
        AgentMcpToolHandler fire = config.executionTriggerFireTool(service);

        assertEquals("metersphere.execution.trigger.create", create.name());
        assertEquals(AgentTokenScope.AI_EXECUTION_RUN, create.requiredScope());
        assertFalse((Boolean) create.annotations().get("readOnlyHint"));
        assertEquals(AgentTokenScope.AI_EXECUTION_RUN, update.requiredScope());
        assertEquals(AgentTokenScope.AI_EXECUTION_READ, list.requiredScope());
        assertTrue((Boolean) list.annotations().get("readOnlyHint"));
        assertEquals(AgentTokenScope.AI_EXECUTION_RUN, fire.requiredScope());
    }
}
