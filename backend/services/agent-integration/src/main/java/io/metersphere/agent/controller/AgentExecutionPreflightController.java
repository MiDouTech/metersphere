package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentExecutionPreflightDTO;
import io.metersphere.agent.dto.AgentExecutionPreflightRequest;
import io.metersphere.agent.service.AgentExecutionPreflightService;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/ai/execution/preflight","/api/ai/execution/preflight"})
public class AgentExecutionPreflightController {
    @Resource private AgentExecutionPreflightService service;
    @PostMapping @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN) public AgentExecutionPreflightDTO preflight(@RequestBody @Valid AgentExecutionPreflightRequest request){return service.preflight(request);}
    @GetMapping("/{id}") @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ) public AgentExecutionPreflightDTO get(@PathVariable String id){return service.get(id);}
}
