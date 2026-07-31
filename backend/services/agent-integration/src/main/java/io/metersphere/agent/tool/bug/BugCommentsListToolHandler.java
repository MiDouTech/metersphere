package io.metersphere.agent.tool.bug;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.bug.service.BugCommentService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BugCommentsListToolHandler implements AgentMcpToolHandler {
    @Resource
    private BugCommentService bugCommentService;

    @Override
    public String name() {
        return "metersphere.bug.comments.list";
    }

    @Override
    public String description() {
        return "List comments of a bug.";
    }

    @Override
    public String requiredScope() {
        return AgentTokenScope.BUG_READ;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AgentMcpToolSchemas.object(
                Map.of("bugId", AgentMcpToolSchemas.string(1, 128)),
                List.of("bugId")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), true, false, false);
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return bugCommentService.getComments((String) arguments.get("bugId"));
    }
}
