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
public class FunctionalAttachmentsListToolHandler implements AgentMcpToolHandler {
    @Resource
    private AgentFunctionalCaseAttachmentService agentFunctionalCaseAttachmentService;

    @Override
    public String name() {
        return "metersphere.functional.attachments.list";
    }

    @Override
    public String description() {
        return "List attachments of a functional test case.";
    }

    @Override
    public String requiredScope() {
        return AgentTokenScope.FUNCTIONAL_READ;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AgentMcpToolSchemas.object(
                Map.of(
                        "projectId", AgentMcpToolSchemas.string(1, 128),
                        "caseId", AgentMcpToolSchemas.string(1, 128)
                ),
                List.of("projectId", "caseId")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), true, false, false);
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return agentFunctionalCaseAttachmentService.list(
                (String) arguments.get("projectId"),
                (String) arguments.get("caseId"));
    }
}
