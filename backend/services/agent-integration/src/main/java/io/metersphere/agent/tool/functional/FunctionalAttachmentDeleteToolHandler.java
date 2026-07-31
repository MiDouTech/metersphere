package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentFunctionalCaseAttachmentService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class FunctionalAttachmentDeleteToolHandler implements AgentMcpToolHandler {
    @Resource
    private AgentFunctionalCaseAttachmentService agentFunctionalCaseAttachmentService;

    @Override
    public String name() {
        return "metersphere.functional.attachment.delete";
    }

    @Override
    public String description() {
        return "Delete an attachment from a functional test case.";
    }

    @Override
    public String requiredScope() {
        return AgentTokenScope.CASE_ATTACHMENT;
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
        return agentFunctionalCaseAttachmentService.delete(
                (String) arguments.get("projectId"),
                (String) arguments.get("resourceId"),
                (String) arguments.get("attachmentId"),
                (Boolean) arguments.get("confirm"));
    }
}
