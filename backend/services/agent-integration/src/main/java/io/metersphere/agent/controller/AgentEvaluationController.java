package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentEvaluationRequest;
import io.metersphere.agent.dto.AgentEvaluationSummaryDTO;
import io.metersphere.agent.dto.AgentExecutionEvaluationDTO;
import io.metersphere.agent.service.AgentEvaluationService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.utils.Pager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/ai/execution/evaluations", "/api/ai/execution/evaluations"})
public class AgentEvaluationController {
    @Resource
    private AgentEvaluationService service;

    @GetMapping
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public Pager<List<AgentExecutionEvaluationDTO>> page(@RequestParam String projectId,
                                                        @RequestParam(required = false) Integer current,
                                                        @RequestParam(required = false) Integer pageSize) {
        return service.page(projectId, current, pageSize);
    }

    @GetMapping("/summary")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public List<AgentEvaluationSummaryDTO> summary(@RequestParam String projectId,
                                                   @RequestParam(required = false) Long fromTime,
                                                   @RequestParam(required = false) Long toTime) {
        return service.summary(projectId, fromTime, toTime);
    }

    @GetMapping("/task/{taskId}")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public AgentExecutionEvaluationDTO get(@PathVariable String taskId) {
        return service.get(taskId);
    }

    @PostMapping("/task/{taskId}/manual")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public AgentExecutionEvaluationDTO manual(@PathVariable String taskId,
                                              @RequestBody @Valid AgentEvaluationRequest request) {
        return service.manualEvaluate(taskId, request);
    }
}
