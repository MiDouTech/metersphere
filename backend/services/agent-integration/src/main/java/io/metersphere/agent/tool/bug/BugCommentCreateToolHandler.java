package io.metersphere.agent.tool.bug;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.bug.dto.request.BugCommentEditRequest;
import io.metersphere.bug.service.BugCommentService;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.notice.constants.NoticeConstants;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BugCommentCreateToolHandler implements AgentMcpToolHandler {
    @Resource
    private BugCommentService bugCommentService;

    @Override
    public String name() {
        return "metersphere.bug.comment.create";
    }

    @Override
    public String description() {
        return "Create a comment or reply for a bug.";
    }

    @Override
    public String requiredScope() {
        return AgentTokenScope.BUG_COMMENT;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AgentMcpToolSchemas.object(
                Map.of(
                        "bugId", AgentMcpToolSchemas.string(1, 128),
                        "content", AgentMcpToolSchemas.string(1, 20000),
                        "event", AgentMcpToolSchemas.enumString(List.of(
                                NoticeConstants.Event.COMMENT,
                                NoticeConstants.Event.AT,
                                NoticeConstants.Event.REPLY
                        )),
                        "parentId", AgentMcpToolSchemas.string(1, 128),
                        "replyUser", AgentMcpToolSchemas.string(1, 128),
                        "notifier", AgentMcpToolSchemas.string(1, 2000),
                        "richTextTmpFileIds", AgentMcpToolSchemas.stringArray(10),
                        "requestId", AgentMcpToolSchemas.string(1, 128)
                ),
                List.of("bugId", "content")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), false, false, true);
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        BugCommentEditRequest request = JSON.parseObject(JSON.toJSONString(arguments), BugCommentEditRequest.class);
        if (StringUtils.isBlank(request.getEvent())) {
            request.setEvent(NoticeConstants.Event.COMMENT);
        }
        return bugCommentService.addComment(request, SessionUtils.getUserId());
    }
}
