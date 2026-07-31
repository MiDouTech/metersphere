package io.metersphere.agent.tool.bug;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentBugWriteService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BugTemplateGetToolHandler implements AgentMcpToolHandler {
    @Resource
    private AgentBugWriteService agentBugWriteService;

    @Override
    public String name() {
        return "metersphere.bug.template.get";
    }

    @Override
    public String description() {
        return "Get the bug template for a project.";
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
                        "templateId", AgentMcpToolSchemas.string(1, 128)
                ),
                List.of("projectId")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), true, false, false);
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return agentBugWriteService.getTemplate(
                (String) arguments.get("projectId"),
                (String) arguments.get("templateId"));
    }
}
