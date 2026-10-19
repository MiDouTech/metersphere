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
    public AgentCaseReviewDTO create(@RequestBody AgentCaseReviewCreateRequest request, jakarta.servlet.http.HttpServletRequest http) {
        request.setRequestId(requestKey(request.getRequestId(),http.getHeader("Idempotency-Key")));
        AgentScopeAssert.assertScope(AgentTokenScope.REVIEW_WRITE);
        return agentCaseReviewWriteService.create(request);
    }

    @PostMapping("/associate-cases")
    @Operation(summary = "关联功能用例到评审")
    public void associate(@RequestBody AgentCaseReviewAssociateRequest request, jakarta.servlet.http.HttpServletRequest http) {
        request.setRequestId(requestKey(request.getRequestId(),http.getHeader("Idempotency-Key")));
        AgentScopeAssert.assertScope(AgentTokenScope.REVIEW_WRITE);
        agentCaseReviewWriteService.associate(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取评审")
    public AgentCaseReviewDTO get(@PathVariable String id) {
        AgentScopeAssert.assertAnyScope(AgentTokenScope.REVIEW_WRITE, AgentTokenScope.FUNCTIONAL_READ);
        return agentCaseReviewWriteService.get(id);
    }
    private String requestKey(String argument,String header) {
        if(org.apache.commons.lang3.StringUtils.isNotBlank(header) && org.apache.commons.lang3.StringUtils.isNotBlank(argument) && !header.equals(argument))throw new io.metersphere.sdk.exception.MSException("IDEMPOTENCY_CONFLICT");
        return org.apache.commons.lang3.StringUtils.defaultIfBlank(header,argument);
    }
}
