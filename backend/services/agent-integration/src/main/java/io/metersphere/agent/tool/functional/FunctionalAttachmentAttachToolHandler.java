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
public class FunctionalAttachmentAttachToolHandler implements AgentMcpToolHandler {
    @Resource
    private AgentFunctionalCaseAttachmentService agentFunctionalCaseAttachmentService;

    @Override
    public String name() {
        return "metersphere.functional.attachment.attach";
    }

    @Override
    public String description() {
        return "Attach uploaded temporary files to a functional test case.";
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
                        "caseId", AgentMcpToolSchemas.string(1, 128),
                        "attachmentIds", AgentMcpToolSchemas.stringArray(10),
                        "requestId", AgentMcpToolSchemas.string(1, 128)
                ),
                List.of("projectId", "caseId", "attachmentIds")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), false, false, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> arguments) {
        return agentFunctionalCaseAttachmentService.attach(
                (String) arguments.get("projectId"),
                (String) arguments.get("caseId"),
                (List<String>) arguments.get("attachmentIds"));
    }
}
