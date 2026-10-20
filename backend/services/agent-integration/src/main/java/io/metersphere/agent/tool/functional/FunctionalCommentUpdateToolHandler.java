package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.service.AgentBusinessAccess;
import io.metersphere.agent.service.AgentOperationExecutor;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.functional.domain.FunctionalCaseComment;
import io.metersphere.functional.mapper.FunctionalCaseCommentMapper;
import io.metersphere.functional.request.FunctionalCaseCommentRequest;
import io.metersphere.functional.service.FunctionalCaseCommentService;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FunctionalCommentUpdateToolHandler implements AgentMcpToolHandler {
    @Resource private AgentBusinessAccess access;
    @Resource private AgentOperationExecutor operations;
    @Resource private FunctionalCaseCommentMapper comments;
    @Resource private FunctionalCaseCommentService service;
    @Resource private io.metersphere.agent.service.AgentExecLogService audit;

    public String name() { return "metersphere.functional.comment.update"; }
    public String description() { return "Edit your own comment text using expectedUpdateTime. Preserves replies, recipients and attachments; does not resend notifications. Explicit CASE_COMMENT_EDIT_V2 required."; }
    public String requiredScope() { return AgentTokenScope.CASE_COMMENT_EDIT_V2; }
    public Map<String,Object> inputSchema() {
        return AgentMcpToolSchemas.object(Map.of(
                "projectId", AgentMcpToolSchemas.string(1,128), "caseId", AgentMcpToolSchemas.string(1,128),
                "commentId", AgentMcpToolSchemas.string(1,128), "content", AgentMcpToolSchemas.string(1,20000),
                "expectedUpdateTime", Map.of("type","integer","minimum",0),
                "requestId", AgentMcpToolSchemas.string(1,128)),
                List.of("projectId","caseId","commentId","content","expectedUpdateTime","requestId"));
    }
    public Map<String,Object> annotations() { return AgentMcpToolSchemas.annotations(requiredScope(),false,true,true); }
    public Object execute(Map<String,Object> args) {
        return operations.execute("functional.comment.update", (String) args.get("requestId"), args,
                FunctionalCaseComment.class, () -> {
                    access.caseProject((String) args.get("caseId"),(String) args.get("projectId"),
                            "FUNCTIONAL_CASE:READ+COMMENT",true,requiredScope());
                    var existing = comments.selectByPrimaryKey((String) args.get("commentId"));
                    if (existing == null || !Objects.equals(existing.getCaseId(),args.get("caseId"))
                            || !Objects.equals(existing.getCreateUser(),AgentTokenContext.get().getUserId())) {
                        throw AgentBusinessAccess.denied();
                    }
                }, () -> {
                    if (!(args.get("expectedUpdateTime") instanceof Number version)
                            || !(args.get("content") instanceof String content) || content.isBlank() || content.length()>20000) {
                        throw new MSException("AGENT_VALIDATION_ERROR");
                    }
                    var existing = comments.selectByPrimaryKey((String) args.get("commentId"));
                    var request = new FunctionalCaseCommentRequest();
                    request.setId(existing.getId()); request.setCaseId(existing.getCaseId());
                    request.setContent(content); request.setExpectedUpdateTime(version.longValue());
                    request.setNotifier(existing.getNotifier()); request.setEvent("COMMENT");
                    var updated = service.updateComment(request,AgentTokenContext.get().getUserId());
                    audit.audit("CASE_COMMENT_UPDATE", existing.getId(), io.metersphere.sdk.util.JSON.toJSONString(Map.of(
                            "caseId", existing.getCaseId(), "expectedUpdateTime", version.longValue(),
                            "updateTime", updated.getUpdateTime(),
                            "beforeHash", org.apache.commons.codec.digest.DigestUtils.sha256Hex(Objects.toString(existing.getContent(),"")),
                            "afterHash", org.apache.commons.codec.digest.DigestUtils.sha256Hex(content))));
                    return updated;
                });
    }
}
