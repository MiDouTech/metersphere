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
public class BugAttachmentAttachToolHandler implements AgentMcpToolHandler {
    @Resource
    private AgentBugAttachmentService agentBugAttachmentService;

    @Override
    public String name() {
        return "metersphere.bug.attachment.attach";
    }

    @Override
    public String description() {
        return "Attach uploaded temporary files to a bug.";
    }

    @Override
    public String requiredScope() {
        return AgentTokenScope.BUG_ATTACHMENT;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AgentMcpToolSchemas.object(
                Map.of(
                        "projectId", AgentMcpToolSchemas.string(1, 128),
                        "bugId", AgentMcpToolSchemas.string(1, 128),
                        "attachmentIds", AgentMcpToolSchemas.stringArray(10),
                        "requestId", AgentMcpToolSchemas.string(1, 128)
                ),
                List.of("projectId", "bugId", "attachmentIds")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), false, false, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> arguments) {
        return agentBugAttachmentService.attach(
                (String) arguments.get("projectId"),
                (String) arguments.get("bugId"),
                (List<String>) arguments.get("attachmentIds"));
    }
}
