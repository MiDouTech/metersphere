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
public class FunctionalCaseUpdateToolHandler implements AgentMcpToolHandler {
    @Resource
    private AgentCaseManageService agentCaseManageService;

    @Override
    public String name() {
        return "metersphere.functional.case.update";
    }

    @Override
    public String description() {
        return "Update a functional test case with a partial patch.";
    }

    @Override
    public String requiredScope() {
        return AgentTokenScope.CASE_UPDATE;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AgentMcpToolSchemas.object(
                Map.of(
                        "projectId", AgentMcpToolSchemas.string(1, 128),
                        "caseId", AgentMcpToolSchemas.string(1, 128),
                        "expectedUpdateTime", AgentMcpToolSchemas.number(),
                        "patch", AgentMcpToolSchemas.objectAny(),
                        "requestId", AgentMcpToolSchemas.string(1, 128),
                        "reason", AgentMcpToolSchemas.string(1, 2000)
                ),
                List.of("projectId", "caseId")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), false, false, true);
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return agentCaseManageService.update(arguments);
    }
}
