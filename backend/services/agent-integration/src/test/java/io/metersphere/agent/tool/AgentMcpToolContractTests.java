package io.metersphere.agent.tool;

import io.metersphere.agent.tool.bug.BugAttachmentAttachToolHandler;
import io.metersphere.agent.tool.bug.BugAttachmentDeleteToolHandler;
import io.metersphere.agent.tool.bug.BugAttachmentsListToolHandler;
import io.metersphere.agent.tool.bug.BugCaseRelateToolHandler;
import io.metersphere.agent.tool.bug.BugCaseUnrelateToolHandler;
import io.metersphere.agent.tool.bug.BugHistoryListToolHandler;
import io.metersphere.agent.tool.bug.BugTemplateGetToolHandler;
import io.metersphere.agent.tool.functional.FunctionalAttachmentAttachToolHandler;
import io.metersphere.agent.tool.functional.FunctionalAttachmentDeleteToolHandler;
import io.metersphere.agent.tool.functional.FunctionalAttachmentsListToolHandler;
import io.metersphere.agent.tool.functional.FunctionalCaseUpdateToolHandler;
import io.metersphere.agent.tool.functional.FunctionalHistoryListToolHandler;
import io.metersphere.agent.tool.functional.FunctionalTemplateGetToolHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class AgentMcpToolContractTests {

    @Test
    void p0ToolsShouldExposeExpectedNamesAndHints() {
        List<AgentMcpToolHandler> handlers = List.of(
                new FunctionalTemplateGetToolHandler(),
                new FunctionalHistoryListToolHandler(),
                new FunctionalAttachmentsListToolHandler(),
                new FunctionalCaseUpdateToolHandler(),
                new FunctionalAttachmentAttachToolHandler(),
                new FunctionalAttachmentDeleteToolHandler(),
                new BugTemplateGetToolHandler(),
                new BugHistoryListToolHandler(),
                new BugAttachmentsListToolHandler(),
                new BugAttachmentAttachToolHandler(),
                new BugAttachmentDeleteToolHandler(),
                new BugCaseRelateToolHandler(),
                new BugCaseUnrelateToolHandler()
        );

        Map<String, Boolean> expectedReadOnly = Map.ofEntries(
                Map.entry("metersphere.functional.template.get", true),
                Map.entry("metersphere.functional.history.list", true),
                Map.entry("metersphere.functional.attachments.list", true),
                Map.entry("metersphere.functional.case.update", false),
                Map.entry("metersphere.functional.attachment.attach", false),
                Map.entry("metersphere.functional.attachment.delete", false),
                Map.entry("metersphere.bug.template.get", true),
                Map.entry("metersphere.bug.history.list", true),
                Map.entry("metersphere.bug.attachments.list", true),
                Map.entry("metersphere.bug.attachment.attach", false),
                Map.entry("metersphere.bug.attachment.delete", false),
                Map.entry("metersphere.bug.case.relate", false),
                Map.entry("metersphere.bug.case.unrelate", false)
        );

        for (AgentMcpToolHandler handler : handlers) {
            Assertions.assertTrue(expectedReadOnly.containsKey(handler.name()), "unexpected tool: " + handler.name());
            Assertions.assertNotNull(handler.inputSchema());
            Assertions.assertEquals(expectedReadOnly.get(handler.name()), handler.annotations().get("readOnlyHint"));
            Assertions.assertNotNull(handler.annotations().get("destructiveHint"));
            Assertions.assertNotNull(handler.annotations().get("idempotentHint"));
        }
    }
}
