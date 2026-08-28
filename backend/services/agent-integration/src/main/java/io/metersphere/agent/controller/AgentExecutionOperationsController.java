package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentExecutionOperationsDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseDTO;
import io.metersphere.agent.service.AgentExecutionOperationsService;
import io.metersphere.agent.service.AgentExecutionMetrics;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/ai/execution/operations", "/api/ai/execution/operations"})
public class AgentExecutionOperationsController {
    @Resource
    private AgentExecutionOperationsService operationsService;
    @Resource
    private AgentExecutionMetrics metrics;
    @Resource
    private io.metersphere.agent.service.AgentExecutionAlertService alerts;

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

    @GetMapping("/metrics")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public List<Map<String,Object>> metrics(@RequestParam(required=false) Long from,
                                            @RequestParam(required=false) Long to) {
        return metrics.summary(from,to);
    }

    @GetMapping("/alerts")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public List<Map<String,Object>> alerts(@RequestParam String projectId,@RequestParam(required=false) String status){return alerts.list(projectId,status);}

    @PostMapping("/alerts/{id}/acknowledge")
    @RequiresPermissions(PermissionConstants.AI_RUNNER_MANAGE)
    public void acknowledge(@PathVariable String id,@RequestParam String projectId){alerts.acknowledge(projectId,id);}
}
