package io.metersphere.agent.controller;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentBugCreateRequest;
import io.metersphere.agent.dto.AgentBugDTO;
import io.metersphere.agent.dto.AgentBugRelateCaseRequest;
import io.metersphere.agent.security.AgentScopeAssert;
import io.metersphere.agent.service.AgentBugWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent Bug")
@RestController
@RequestMapping({"/agent/v1/bug", "/api/agent/v1/bug"})
public class AgentBugController {
    @Resource
    private AgentBugWriteService agentBugWriteService;

    @PostMapping("/create")
    @Operation(summary = "创建缺陷并可关联用例")
    public AgentBugDTO create(@RequestBody @Valid AgentBugCreateRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.BUG_WRITE);
        return agentBugWriteService.create(request);
    }

    @PostMapping("/relate-case")
    @Operation(summary = "缺陷关联用例")
    public void relateCase(@RequestBody @Valid AgentBugRelateCaseRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.BUG_WRITE);
        agentBugWriteService.relateCase(request);
    }
}
