package io.metersphere.agent.controller;

import io.metersphere.agent.dto.*;
import io.metersphere.agent.service.AgentCaseExecutabilityService;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/ai/case-executability")
public class AgentCaseExecutabilityController {
    @Resource private AgentCaseExecutabilityService service;
    @PostMapping("/check") @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public List<AgentCaseExecutabilityDTO> check(@Validated @RequestBody AgentCaseExecutabilityRequest request){return service.batchCheck(request);}
    @PostMapping("/config") @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public AgentCaseExecutabilityDTO save(@Validated @RequestBody AgentCaseExecutabilityConfigRequest request){return service.save(request);}
}
