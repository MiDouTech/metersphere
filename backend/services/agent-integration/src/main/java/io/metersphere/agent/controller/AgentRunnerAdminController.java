package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentRunnerRegisterRequest;
import io.metersphere.agent.dto.AgentRunnerRegisterResponse;
import io.metersphere.agent.service.AgentRunnerService;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.metersphere.agent.dto.AgentRunnerDTO;
import java.util.List;

@RestController
@RequestMapping({"/ai/runner", "/api/ai/runner"})
public class AgentRunnerAdminController {
    @Resource
    private AgentRunnerService runnerService;

    @GetMapping
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public List<AgentRunnerDTO> list() {
        return runnerService.list();
    }

    @PostMapping("/register")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_ADMIN)
    public AgentRunnerRegisterResponse register(@RequestBody @Valid AgentRunnerRegisterRequest request) {
        return runnerService.register(request);
    }
}
