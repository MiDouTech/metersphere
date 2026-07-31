package io.metersphere.agent.tool.bug;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentBugRelateCaseRequest;
import io.metersphere.agent.service.AgentBugWriteService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.sdk.constants.CaseType;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BugCaseRelateToolHandler implements AgentMcpToolHandler {
    @Resource
    private AgentBugWriteService agentBugWriteService;

    @Override
    public String name() {
        return "metersphere.bug.case.relate";
    }

    @Override
    public String description() {
        return "Relate functional test cases to a bug.";
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
                        "bugId", AgentMcpToolSchemas.string(1, 128),
                        "caseIds", AgentMcpToolSchemas.stringArray(100),
                        "caseType", AgentMcpToolSchemas.enumString(List.of(CaseType.FUNCTIONAL_CASE.getKey())),
                        "requestId", AgentMcpToolSchemas.string(1, 128)
                ),
                List.of("projectId", "bugId", "caseIds")
        );
    }

    @Override
    public Map<String, Object> annotations() {
        return AgentMcpToolSchemas.annotations(requiredScope(), false, false, true);
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        AgentBugRelateCaseRequest request = JSON.parseObject(JSON.toJSONString(arguments), AgentBugRelateCaseRequest.class);
        if (StringUtils.isBlank(request.getCaseType())) {
            request.setCaseType(CaseType.FUNCTIONAL_CASE.getKey());
        }
        agentBugWriteService.relateCase(request);
        return Map.of("ok", true);
    }
}
