package io.metersphere.agent.controller;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentCaseReviewAssociateRequest;
import io.metersphere.agent.dto.AgentCaseReviewCreateRequest;
import io.metersphere.agent.dto.AgentCaseReviewDTO;
import io.metersphere.agent.security.AgentScopeAssert;
import io.metersphere.agent.service.AgentCaseReviewWriteService;
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

@Tag(name = "Agent Case Review")
@RestController
@RequestMapping({"/agent/v1/case-review", "/api/agent/v1/case-review"})
public class AgentCaseReviewController {
    @Resource
    private AgentCaseReviewWriteService agentCaseReviewWriteService;

    @PostMapping("/create")
    @Operation(summary = "创建用例评审")
    public AgentCaseReviewDTO create(@RequestBody @Valid AgentCaseReviewCreateRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.REVIEW_WRITE);
        return agentCaseReviewWriteService.create(request);
    }

    @PostMapping("/associate-cases")
    @Operation(summary = "关联功能用例到评审")
    public void associate(@RequestBody @Valid AgentCaseReviewAssociateRequest request) {
        AgentScopeAssert.assertScope(AgentTokenScope.REVIEW_WRITE);
        agentCaseReviewWriteService.associate(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取评审")
    public AgentCaseReviewDTO get(@PathVariable String id) {
        AgentScopeAssert.assertScope(AgentTokenScope.REVIEW_WRITE);
        return agentCaseReviewWriteService.get(id);
    }
}
