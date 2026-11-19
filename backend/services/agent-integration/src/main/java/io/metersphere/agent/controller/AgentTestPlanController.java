package io.metersphere.agent.controller;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentCaseSearchResponse;
import io.metersphere.agent.dto.AgentTestPlanAssociateRequest;
import io.metersphere.agent.dto.AgentTestPlanCreateRequest;
import io.metersphere.agent.dto.AgentTestPlanDTO;
import io.metersphere.agent.dto.AgentTestPlanSearchRequest;
import io.metersphere.agent.dto.AgentTestPlanSearchResponse;
import io.metersphere.agent.security.AgentScopeAssert;
import io.metersphere.agent.service.AgentTestPlanQueryService;
import io.metersphere.agent.service.AgentTestPlanWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestParam;
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
    @Resource
    private AgentTestPlanQueryService agentTestPlanQueryService;

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
        AgentScopeAssert.assertScope(AgentTokenScope.PLAN_READ);
        return agentTestPlanWriteService.get(id);
    }

    @PostMapping("/search")
    @Operation(summary = "检索测试计划")
    public AgentTestPlanSearchResponse search(@RequestBody AgentTestPlanSearchRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.PLAN_READ);
        return agentTestPlanQueryService.search(request);
    }

    @GetMapping("/{id}/cases/{relationId}/execution-contract")
    @Operation(summary = "读取指定计划用例版本的已发布执行契约")
    public Map<String, Object> executionContract(@PathVariable String id, @PathVariable String relationId,
                                                 @RequestParam String projectId, @RequestParam Long contractVersion) {
        AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ);
        return agentTestPlanQueryService.readExecutionContract(projectId, id, relationId, contractVersion);
    }

    @GetMapping("/{id}/cases")
    @Operation(summary = "获取测试计划下的功能用例")
    public AgentCaseSearchResponse cases(@PathVariable String id,
                                         String projectId,
                                         Integer current,
                                         Integer pageSize,
                                         Boolean includeSteps,
                                         Boolean includeContractVersion) {
        AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ);
        return agentTestPlanQueryService.cases(projectId, id,
                current == null ? 1 : current,
                pageSize == null ? 50 : pageSize,
                includeSteps == null || includeSteps, Boolean.TRUE.equals(includeContractVersion));
    }
}
