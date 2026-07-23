package io.metersphere.agent.controller;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentTestPlanAssociateRequest;
import io.metersphere.agent.dto.AgentTestPlanCreateRequest;
import io.metersphere.agent.dto.AgentTestPlanDTO;
import io.metersphere.agent.security.AgentScopeAssert;
import io.metersphere.agent.service.AgentTestPlanWriteService;
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

@Tag(name = "Agent Test Plan")
@RestController
@RequestMapping({"/agent/v1/test-plan", "/api/agent/v1/test-plan"})
public class AgentTestPlanController {
    @Resource
    private AgentTestPlanWriteService agentTestPlanWriteService;

    @PostMapping("/create")
    @Operation(summary = "创建测试计划")
    public AgentTestPlanDTO create(@RequestBody @Valid AgentTestPlanCreateRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.PLAN_WRITE);
        return agentTestPlanWriteService.create(request);
    }

    @PostMapping("/associate-cases")
    @Operation(summary = "关联功能用例到测试计划")
    public void associate(@RequestBody @Valid AgentTestPlanAssociateRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.PLAN_WRITE);
        agentTestPlanWriteService.associate(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取测试计划")
    public AgentTestPlanDTO get(@PathVariable String id) {
        AgentScopeAssert.assertScope(AgentTokenScope.PLAN_WRITE);
        return agentTestPlanWriteService.get(id);
    }
}
