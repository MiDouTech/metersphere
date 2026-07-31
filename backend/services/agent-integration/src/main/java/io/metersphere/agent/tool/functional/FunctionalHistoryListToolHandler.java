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
public class FunctionalHistoryListToolHandler implements AgentMcpToolHandler {
    @Resource
    private AgentCaseManageService agentCaseManageService;

    @Override
    public String name() {
        return "metersphere.functional.history.list";
    }

    @Override
    public String description() {
        return "List change history of a functional test case.";
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
                        "caseId", AgentMcpToolSchemas.string(1, 128),
                        "current", AgentMcpToolSchemas.number(),
                        "pageSize", AgentMcpToolSchemas.number()
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
        Number current = (Number) arguments.get("current");
        Number pageSize = (Number) arguments.get("pageSize");
        return agentCaseManageService.listHistory(
                (String) arguments.get("projectId"),
                (String) arguments.get("caseId"),
                current == null ? null : current.intValue(),
                pageSize == null ? null : pageSize.intValue());
    }
}
