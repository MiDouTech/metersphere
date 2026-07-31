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

    @Override
    public String name() {
        return "metersphere.functional.comment.create";
    }

    @Override
    public String description() {
        return "Create a comment or reply for a functional test case.";
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
                List.of("caseId", "projectId", "content")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), false, false, true);
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        FunctionalCaseCommentRequest request = JSON.parseObject(JSON.toJSONString(arguments), FunctionalCaseCommentRequest.class);
        if (StringUtils.isBlank(request.getEvent())) {
            request.setEvent(NoticeConstants.Event.COMMENT);
        }
        return functionalCaseCommentService.saveComment(request, SessionUtils.getUserId());
    }
}
