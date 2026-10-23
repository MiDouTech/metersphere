package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.service.AgentBusinessAccess;
import io.metersphere.agent.service.AgentExecLogService;
import io.metersphere.agent.service.AgentOperationExecutor;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.functional.service.FunctionalCaseCommentAccess;
import io.metersphere.functional.service.FunctionalCaseCommentLifecycleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class FunctionalCommentDeleteToolHandler implements AgentMcpToolHandler {
    @Resource private AgentBusinessAccess access;
    @Resource private AgentOperationExecutor operations;
    @Resource private FunctionalCaseCommentLifecycleService comments;
    @Resource private AgentExecLogService audit;
    public String name() { return "metersphere.functional.comment.delete"; }
    public String description() { return "Delete a versioned comment into a tombstone while preserving replies. Author or comment moderator only; explicit CASE_COMMENT_DELETE_V2 and requestId required. AGENT_ALL does not include this grant."; }
    public String requiredScope() { return AgentTokenScope.CASE_COMMENT_DELETE_V2; }
    public Map<String, Object> inputSchema() {
        return AgentMcpToolSchemas.object(Map.of("projectId", AgentMcpToolSchemas.string(1,128),
                "caseId", AgentMcpToolSchemas.string(1,128), "commentId", AgentMcpToolSchemas.string(1,128),
                "expectedUpdateTime", Map.of("type", "integer", "minimum", 0), "requestId", AgentMcpToolSchemas.string(1,128)),
                List.of("projectId", "caseId", "commentId", "expectedUpdateTime", "requestId"));
    }
    public Map<String, Object> annotations() { return AgentMcpToolSchemas.annotations(requiredScope(), false, true, true); }
    public Object execute(Map<String, Object> args) {
        String caseId = (String) args.get("caseId"); String commentId = (String) args.get("commentId");
        String userId = AgentTokenContext.get().getUserId();
        return operations.execute("functional.comment.delete", (String) args.get("requestId"), args,
                FunctionalCaseCommentLifecycleService.DeletedComment.class, () -> {
                    access.caseProject(caseId, (String) args.get("projectId"), "FUNCTIONAL_CASE:READ+COMMENT", true, requiredScope());
                    comments.requireDeletePermission(caseId, commentId, userId);
                }, () -> {
                    if (!(args.get("expectedUpdateTime") instanceof Number version) || version.longValue() < 0
                            || version.doubleValue() != version.longValue()) throw FunctionalCaseCommentAccess.invalid("请提供有效的评论版本");
                    var result = comments.delete(caseId, commentId, userId, version.longValue());
                    audit.audit("CASE_COMMENT_DELETE", commentId, io.metersphere.sdk.util.JSON.toJSONString(Map.of("caseId", caseId, "updateTime", result.updateTime())));
                    return result;
                });
    }
}
