package io.metersphere.agent.tool.bug;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentBugAttachmentService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BugAttachmentsListToolHandler implements AgentMcpToolHandler {
    @Resource
    private AgentBugAttachmentService agentBugAttachmentService;

    @Override
    public String name() {
        return "metersphere.bug.attachments.list";
    }

    @Override
    public String description() {
        return "List attachments of a bug.";
    }

    @Override
    public String requiredScope() {
        return AgentTokenScope.BUG_READ;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AgentMcpToolSchemas.object(
                Map.of(
                        "projectId", AgentMcpToolSchemas.string(1, 128),
                        "bugId", AgentMcpToolSchemas.string(1, 128)
                ),
                List.of("projectId", "bugId")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), true, false, false);
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return agentBugAttachmentService.list(
                (String) arguments.get("projectId"),
                (String) arguments.get("bugId"));
    }
}
