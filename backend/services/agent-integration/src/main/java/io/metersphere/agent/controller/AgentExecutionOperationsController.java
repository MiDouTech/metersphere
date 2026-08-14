package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentExecutionOperationsDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseDTO;
import io.metersphere.agent.service.AgentExecutionOperationsService;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@RestController
@RequestMapping({"/ai/execution/operations", "/api/ai/execution/operations"})
public class AgentExecutionOperationsController {
    @Resource
    private AgentExecutionOperationsService operationsService;

    @GetMapping("/summary")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public AgentExecutionOperationsDTO summary() {
        return operationsService.summary();
    }

    @GetMapping("/leases")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public List<AgentRunnerLeaseDTO> leases(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) Integer limit) {
        return operationsService.leases(status, limit);
    }
}
