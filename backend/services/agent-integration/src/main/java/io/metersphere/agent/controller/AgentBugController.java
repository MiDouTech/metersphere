package io.metersphere.agent.controller;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentBugCreateRequest;
import io.metersphere.agent.dto.AgentBugDTO;
import io.metersphere.agent.dto.AgentBugRelateCaseRequest;
import io.metersphere.agent.dto.AgentBugSearchRequest;
import io.metersphere.agent.dto.AgentBugSearchResponse;
import io.metersphere.agent.dto.AgentBugUpdateRequest;
import io.metersphere.bug.dto.request.BugTransitionRequest;
import io.metersphere.bug.dto.response.BugTransitionDTO;
import io.metersphere.agent.security.AgentScopeAssert;
import io.metersphere.agent.service.AgentBugWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @PostMapping("/search")
    @Operation(summary = "检索缺陷列表")
    public AgentBugSearchResponse search(@RequestBody @Valid AgentBugSearchRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.BUG_READ);
        return agentBugWriteService.search(request);
    }

    @GetMapping("/{bugId}")
    @Operation(summary = "获取缺陷详情")
    public AgentBugDTO get(@PathVariable String bugId) {
        AgentScopeAssert.assertScope(AgentTokenScope.BUG_READ);
        return agentBugWriteService.get(bugId);
    }

    @PostMapping("/create")
    @Operation(summary = "创建缺陷并可关联用例")
    public AgentBugDTO create(@RequestBody @Valid AgentBugCreateRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.BUG_WRITE);
        return agentBugWriteService.create(request);
    }

    @PostMapping("/update")
    @Operation(summary = "更新缺陷")
    public AgentBugDTO update(@RequestBody @Valid AgentBugUpdateRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.BUG_WRITE);
        return agentBugWriteService.update(request);
    }

    @GetMapping("/{projectId}/{bugId}/transitions")
    @Operation(summary = "获取缺陷当前可执行流转")
    public BugTransitionDTO transitions(@PathVariable String projectId, @PathVariable String bugId) {
        AgentScopeAssert.assertScope(AgentTokenScope.BUG_READ);
        return agentBugWriteService.getTransitions(projectId, bugId);
    }

    @PostMapping("/{projectId}/{bugId}/transition")
    @Operation(summary = "按流转 ID 更新缺陷状态")
    public BugTransitionDTO transition(@PathVariable String projectId, @PathVariable String bugId,
                                       @RequestBody @Valid BugTransitionRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.BUG_WRITE);
        return agentBugWriteService.transition(projectId, bugId, request);
    }

    @PostMapping("/relate-case")
    @Operation(summary = "缺陷关联用例")
    public void relateCase(@RequestBody @Valid AgentBugRelateCaseRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.BUG_RELATE);
        agentBugWriteService.relateCase(request);
    }
}
