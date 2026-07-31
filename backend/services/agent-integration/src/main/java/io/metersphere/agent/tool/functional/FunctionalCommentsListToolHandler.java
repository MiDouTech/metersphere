package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.functional.service.FunctionalCaseCommentService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class FunctionalCommentsListToolHandler implements AgentMcpToolHandler {
    @Resource
    private FunctionalCaseCommentService functionalCaseCommentService;

    @Override
    public String name() {
        return "metersphere.functional.comments.list";
    }

    @Override
    public String description() {
        return "List comments of a functional test case.";
    }

    @Override
    public String requiredScope() {
        return AgentTokenScope.FUNCTIONAL_READ;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AgentMcpToolSchemas.object(
                Map.of("caseId", AgentMcpToolSchemas.string(1, 128)),
                List.of("caseId")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), true, false, false);
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return functionalCaseCommentService.getCommentList((String) arguments.get("caseId"));
    }
}
