package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.functional.request.FunctionalCaseCommentRequest;
import io.metersphere.functional.service.FunctionalCaseCommentService;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.notice.constants.NoticeConstants;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class FunctionalCommentCreateToolHandler implements AgentMcpToolHandler {
    @Resource
    private FunctionalCaseCommentService functionalCaseCommentService;

    @Resource
    private io.metersphere.agent.service.AgentBusinessAccess access;

    @Resource
    private io.metersphere.agent.service.AgentOperationExecutor operations;
    @Resource
    private io.metersphere.agent.service.AgentTempAttachmentService attachments;

    @Override
    public String name() {
        return "metersphere.functional.comment.create";
    }

    @Override
    public String description() {
        return "Create a comment or reply. uploadFileIds must contain temporary attachment IDs returned by the Agent upload endpoint, not storage file IDs.";
    }

    @Override
    public String requiredScope() {
        return AgentTokenScope.CASE_COMMENT;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AgentMcpToolSchemas.object(
                Map.of(
                        "caseId", AgentMcpToolSchemas.string(1, 128),
                        "projectId", AgentMcpToolSchemas.string(1, 128),
                        "content", AgentMcpToolSchemas.string(1, 20000),
                        "event", AgentMcpToolSchemas.enumString(List.of(
                                NoticeConstants.Event.COMMENT,
                                NoticeConstants.Event.AT,
                                NoticeConstants.Event.REPLY
                        )),
                        "parentId", AgentMcpToolSchemas.string(1, 128),
                        "replyUser", AgentMcpToolSchemas.string(1, 128),
                        "notifier", AgentMcpToolSchemas.string(1, 2000),
                        "uploadFileIds", AgentMcpToolSchemas.stringArray(10),
                        "requestId", AgentMcpToolSchemas.string(1, 128)
                ),
                List.of("caseId", "projectId", "content", "requestId")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), false, false, true);
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return operations.execute("functional.comment.create", (String) arguments.get("requestId"), arguments,
                io.metersphere.functional.domain.FunctionalCaseComment.class,
                () -> access.caseProject((String) arguments.get("caseId"), (String) arguments.get("projectId"),
                        "FUNCTIONAL_CASE:READ+COMMENT", true, AgentTokenScope.CASE_COMMENT),
                () -> createOnce(arguments));
    }

    private io.metersphere.functional.domain.FunctionalCaseComment createOnce(Map<String, Object> arguments) {
        FunctionalCaseCommentRequest request = JSON.parseObject(JSON.toJSONString(arguments), FunctionalCaseCommentRequest.class);
        if (StringUtils.isBlank(request.getEvent())) {
            request.setEvent(NoticeConstants.Event.COMMENT);
        }
        access.caseProject(request.getCaseId(), request.getProjectId(), "FUNCTIONAL_CASE:READ+COMMENT", true, AgentTokenScope.CASE_COMMENT);
        if (StringUtils.isBlank(request.getContent()) || request.getContent().length()>20000
                || !List.of(NoticeConstants.Event.COMMENT, NoticeConstants.Event.AT, NoticeConstants.Event.REPLY).contains(request.getEvent())) {
            throw new io.metersphere.sdk.exception.MSException("AGENT_VALIDATION_ERROR");
        }
        if (NoticeConstants.Event.REPLY.equals(request.getEvent())) {
            access.commentParent(request.getCaseId(), request.getParentId(), request.getReplyUser());
        } else if (StringUtils.isNotBlank(request.getParentId()) || StringUtils.isNotBlank(request.getReplyUser())) {
            throw new io.metersphere.sdk.exception.MSException("AGENT_VALIDATION_ERROR");
        }
        if (StringUtils.isNotBlank(request.getNotifier())) {
            String[] recipients=request.getNotifier().split(";",-1);
            if (recipients.length>100) throw new io.metersphere.sdk.exception.MSException("AGENT_VALIDATION_ERROR");
            for (String recipient:recipients) {
                if (StringUtils.isBlank(recipient) || !access.userHasPermission(recipient, request.getProjectId(), "FUNCTIONAL_CASE:READ")) {
                    throw io.metersphere.agent.service.AgentBusinessAccess.denied();
                }
            }
        }
        var files = attachments.requireUsable(request.getUploadFileIds(), request.getProjectId(), io.metersphere.agent.constants.AgentAttachmentPurpose.CASE_COMMENT);
        attachments.markLinked(files);
        request.setUploadFileIds(attachments.toFileIds(files));
        return functionalCaseCommentService.saveComment(request, io.metersphere.agent.security.AgentTokenContext.get().getUserId());
    }
}
