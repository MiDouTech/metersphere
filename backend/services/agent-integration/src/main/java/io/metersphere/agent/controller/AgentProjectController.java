package io.metersphere.agent.controller;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentProjectAddMembersRequest;
import io.metersphere.agent.dto.AgentProjectCreateRequest;
import io.metersphere.agent.dto.AgentProjectDTO;
import io.metersphere.agent.security.AgentScopeAssert;
import io.metersphere.agent.service.AgentProjectService;
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

@Tag(name = "Agent Project")
@RestController
@RequestMapping({"/agent/v1/project", "/api/agent/v1/project"})
public class AgentProjectController {
    @Resource
    private AgentProjectService agentProjectService;

    @PostMapping("/create")
    @Operation(summary = "创建项目")
    public AgentProjectDTO create(@RequestBody @Valid AgentProjectCreateRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.PROJECT_WRITE);
        return agentProjectService.create(request);
    }

    @PostMapping("/members/add")
    @Operation(summary = "添加项目成员")
    public void addMembers(@RequestBody @Valid AgentProjectAddMembersRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.PROJECT_WRITE);
        agentProjectService.addMembers(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取项目信息")
    public AgentProjectDTO get(@PathVariable String id) {
        AgentScopeAssert.assertScope(AgentTokenScope.PROJECT_WRITE);
        return agentProjectService.get(id);
    }
}
