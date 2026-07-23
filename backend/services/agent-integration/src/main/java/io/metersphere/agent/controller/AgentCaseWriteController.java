package io.metersphere.agent.controller;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentCaseBatchCreateRequest;
import io.metersphere.agent.dto.AgentCaseBatchCreateResponse;
import io.metersphere.agent.dto.AgentCaseCreateRequest;
import io.metersphere.agent.dto.AgentModuleCreateRequest;
import io.metersphere.agent.dto.AgentModuleDTO;
import io.metersphere.agent.security.AgentScopeAssert;
import io.metersphere.agent.service.AgentCaseWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent Case Write")
@RestController
@RequestMapping({"/agent/v1/functional", "/api/agent/v1/functional"})
public class AgentCaseWriteController {
    @Resource
    private AgentCaseWriteService agentCaseWriteService;

    @PostMapping("/module/create")
    @Operation(summary = "创建功能用例模块")
    public AgentModuleDTO createModule(@RequestBody @Valid AgentModuleCreateRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.CASE_WRITE);
        return agentCaseWriteService.createModule(request);
    }

    @PostMapping("/case/create")
    @Operation(summary = "创建单条功能用例")
    public AgentCaseBatchCreateResponse.CreatedCase createCase(@RequestBody @Valid AgentCaseCreateRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.CASE_WRITE);
        return agentCaseWriteService.createCase(request);
    }

    @PostMapping("/case/batch-create")
    @Operation(summary = "批量创建功能用例")
    public AgentCaseBatchCreateResponse batchCreate(@RequestBody @Valid AgentCaseBatchCreateRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.CASE_WRITE);
        return agentCaseWriteService.batchCreate(request);
    }
}
