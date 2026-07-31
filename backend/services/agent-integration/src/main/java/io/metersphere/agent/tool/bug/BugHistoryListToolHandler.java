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
public class BugHistoryListToolHandler implements AgentMcpToolHandler {
    @Resource
    private AgentBugWriteService agentBugWriteService;

    @Override
    public String name() {
        return "metersphere.bug.history.list";
    }

    @Override
    public String description() {
        return "List change history of a bug.";
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
                        "bugId", AgentMcpToolSchemas.string(1, 128),
                        "current", AgentMcpToolSchemas.number(),
                        "pageSize", AgentMcpToolSchemas.number()
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
        Number current = (Number) arguments.get("current");
        Number pageSize = (Number) arguments.get("pageSize");
        return agentBugWriteService.listHistory(
                (String) arguments.get("projectId"),
                (String) arguments.get("bugId"),
                current == null ? null : current.intValue(),
                pageSize == null ? null : pageSize.intValue());
    }
}
