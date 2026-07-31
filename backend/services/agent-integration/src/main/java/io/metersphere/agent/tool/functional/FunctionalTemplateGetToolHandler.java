package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentCaseManageService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class FunctionalTemplateGetToolHandler implements AgentMcpToolHandler {
    @Resource
    private AgentCaseManageService agentCaseManageService;

    @Override
    public String name() {
        return "metersphere.functional.template.get";
    }

    @Override
    public String description() {
        return "Get the functional case template for a project.";
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
        return agentCaseManageService.getTemplate(
                (String) arguments.get("projectId"),
                (String) arguments.get("templateId"));
    }
}
