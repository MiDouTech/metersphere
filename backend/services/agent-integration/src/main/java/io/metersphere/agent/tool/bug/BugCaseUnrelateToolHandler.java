package io.metersphere.agent.tool.bug;

import io.metersphere.agent.constants.AgentErrorCode;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentBugWriteService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BugCaseUnrelateToolHandler implements AgentMcpToolHandler {
    @Resource
    private AgentBugWriteService agentBugWriteService;

    @Override
    public String name() {
        return "metersphere.bug.case.unrelate";
    }

    @Override
    public String description() {
        return "Unrelate a functional test case from a bug.";
    }

    @Override
    public String requiredScope() {
        return AgentTokenScope.BUG_RELATE;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return AgentMcpToolSchemas.object(
                Map.of(
                        "projectId", AgentMcpToolSchemas.string(1, 128),
                        "relationId", AgentMcpToolSchemas.string(1, 128),
                        "confirm", Map.of("type", "boolean"),
                        "requestId", AgentMcpToolSchemas.string(1, 128)
                ),
                List.of("projectId", "relationId", "confirm")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), false, true, true);
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        if (!BooleanUtils.isTrue((Boolean) arguments.get("confirm"))) {
            throw new MSException(AgentErrorCode.CONFIRMATION_REQUIRED, "解除关联需要 confirm=true");
        }
        agentBugWriteService.unrelateCase((String) arguments.get("relationId"));
        return Map.of("ok", true);
    }
}
