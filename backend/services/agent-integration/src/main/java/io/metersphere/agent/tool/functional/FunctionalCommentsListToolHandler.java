package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.functional.service.FunctionalCaseCommentLifecycleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class FunctionalCommentsListToolHandler implements AgentMcpToolHandler {
    @Resource(name = "functionalCaseCommentLifecycleService")
    private FunctionalCaseCommentLifecycleService functionalCaseCommentService;

    @Resource
    private io.metersphere.agent.service.AgentBusinessAccess access;

    @Override
    public String name() {
        return "metersphere.functional.comments.list";
    }

    @Override
    public String description() {
        return "Page case comments (default 10, maximum 100). Returns {list,total,current,pageSize}; roots include the first 10 replies and childTotal. Supply parentId to page replies. Deleted comments are tombstones and retain replies.";
    }

    @Override
    public String requiredScope() {
        return AgentTokenScope.FUNCTIONAL_READ;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AgentMcpToolSchemas.object(
                Map.of("caseId", AgentMcpToolSchemas.string(1, 128),
                        "projectId", AgentMcpToolSchemas.string(1, 128),
                        "parentId", AgentMcpToolSchemas.string(1, 128),
                        "current", Map.of("type", "integer", "minimum", 1, "maximum", Integer.MAX_VALUE),
                        "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 100)),
                List.of("caseId")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), true, false, false);
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        access.caseProject((String) arguments.get("caseId"), (String) arguments.get("projectId"), "FUNCTIONAL_CASE:READ", false, AgentTokenScope.FUNCTIONAL_READ);
        return functionalCaseCommentService.page((String) arguments.get("caseId"), io.metersphere.agent.security.AgentTokenContext.get().getUserId(),
                (String) arguments.get("parentId"), pageNumber(arguments.get("current"), 1, Integer.MAX_VALUE), pageNumber(arguments.get("pageSize"), 10, 100));
    }

    private int pageNumber(Object value, int fallback, int maximum) {
        if (value == null) return fallback;
        if (!(value instanceof Number number) || number.doubleValue() < 1 || number.doubleValue() > maximum
                || number.doubleValue() != number.intValue()) throw io.metersphere.functional.service.FunctionalCaseCommentAccess.invalid("评论分页参数无效");
        return number.intValue();
    }
}
