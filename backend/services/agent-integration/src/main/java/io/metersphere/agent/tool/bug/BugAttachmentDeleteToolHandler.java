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
public class BugAttachmentDeleteToolHandler implements AgentMcpToolHandler {
    @Resource
    private AgentBugAttachmentService agentBugAttachmentService;

    @Override
    public String name() {
        return "metersphere.bug.attachment.delete";
    }

    @Override
    public String description() {
        return "Delete an attachment from a bug.";
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
                        "resourceId", AgentMcpToolSchemas.string(1, 128),
                        "attachmentId", AgentMcpToolSchemas.string(1, 128),
                        "confirm", Map.of("type", "boolean"),
                        "requestId", AgentMcpToolSchemas.string(1, 128)
                ),
                List.of("projectId", "resourceId", "attachmentId", "confirm")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), false, true, true);
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return agentBugAttachmentService.delete(
                (String) arguments.get("projectId"),
                (String) arguments.get("resourceId"),
                (String) arguments.get("attachmentId"),
                (Boolean) arguments.get("confirm"));
    }
}
