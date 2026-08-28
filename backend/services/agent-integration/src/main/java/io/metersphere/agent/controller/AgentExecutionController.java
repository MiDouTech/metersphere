package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentExecutionActionRequest;
import io.metersphere.agent.dto.AgentExecutionCreateRequest;
import io.metersphere.agent.dto.AgentExecutionEventsRequest;
import io.metersphere.agent.dto.AgentExecutionEventsResponse;
import io.metersphere.agent.dto.AgentExecutionResolveRequest;
import io.metersphere.agent.dto.AgentExecutionResolveResponse;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentExecutionTaskSearchRequest;
import io.metersphere.agent.dto.AgentExecutionTaskSearchResponse;
import io.metersphere.agent.dto.AgentHumanRequestDTO;
import io.metersphere.agent.dto.AgentHumanResponseRequest;
import io.metersphere.agent.service.AgentExecutionService;
import io.metersphere.agent.service.AgentHumanRequestService;
import io.metersphere.agent.service.AgentExecutionObservabilityService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.request.ai.AiAgentGatewayCapabilityDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import io.metersphere.system.utils.SessionUtils;

import java.util.List;
import java.util.Map;

@Tag(name = "AI Execution")
@RestController
@RequestMapping({"/ai/execution", "/api/ai/execution"})
public class AgentExecutionController {
    @Resource
    private AgentExecutionService agentExecutionService;
    @Resource
    private AgentHumanRequestService humanRequestService;
    @Resource
    private AgentExecutionObservabilityService observabilityService;

    @GetMapping("/agents")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    @Operation(summary = "获取可用于 Web UI 自动化执行的 Agent")
    public List<AiAgentGatewayCapabilityDTO> agents(@RequestParam String projectId) {
        return agentExecutionService.executionAgents(projectId);
    }

    @PostMapping("/resolve")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    @Operation(summary = "解析 AI 自动化执行范围")
    public AgentExecutionResolveResponse resolve(@RequestBody AgentExecutionResolveRequest request) {
        return agentExecutionService.resolve(request);
    }

    @PostMapping("/task")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    @Operation(summary = "创建 AI 自动化执行任务")
    public AgentExecutionTaskDTO create(@RequestBody @Valid AgentExecutionCreateRequest request) {
        return agentExecutionService.create(request);
    }

    @PostMapping("/task/search")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    @Operation(summary = "分页查询 AI 自动化执行任务")
    public AgentExecutionTaskSearchResponse search(@RequestBody AgentExecutionTaskSearchRequest request) {
        return agentExecutionService.searchTasks(request);
    }

    @GetMapping("/task/{id}")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    @Operation(summary = "获取 AI 自动化执行任务")
    public AgentExecutionTaskDTO get(@PathVariable String id) {
        return agentExecutionService.get(id);
    }

    @GetMapping("/task/{id}/observability")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public Map<String,Object> observability(@PathVariable String id) {
        return observabilityService.detail(id);
    }

    @GetMapping("/task/{id}/events")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    @Operation(summary = "获取 AI 自动化执行任务事件")
    public AgentExecutionEventsResponse events(@PathVariable String id,
                                               @org.springframework.web.bind.annotation.ModelAttribute AgentExecutionEventsRequest request) {
        return agentExecutionService.events(id, request);
    }

    @PostMapping("/task/{id}/confirm")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    @Operation(summary = "确认 AI 自动化执行任务")
    public AgentExecutionTaskDTO confirm(@PathVariable String id, @RequestBody(required = false) AgentExecutionActionRequest request) {
        return agentExecutionService.confirm(id, request == null ? null : request.getReason());
    }

    @PostMapping("/task/{id}/login-ready")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_LOGIN)
    @Operation(summary = "人工登录完成后恢复 AI 自动化执行任务")
    public AgentExecutionTaskDTO loginReady(@PathVariable String id, @RequestBody(required = false) AgentExecutionActionRequest request) {
        return agentExecutionService.loginReady(id, request == null ? null : request.getReason());
    }

    @PostMapping("/task/{id}/pause")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    @Operation(summary = "暂停 AI 自动化执行任务")
    public AgentExecutionTaskDTO pause(@PathVariable String id, @RequestBody(required = false) AgentExecutionActionRequest request) {
        return agentExecutionService.pause(id, request == null ? null : request.getReason());
    }

    @PostMapping("/task/{id}/cancel")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_CANCEL)
    @Operation(summary = "取消 AI 自动化执行任务")
    public AgentExecutionTaskDTO cancel(@PathVariable String id, @RequestBody(required = false) AgentExecutionActionRequest request) {
        return agentExecutionService.cancel(id, request == null ? null : request.getReason());
    }

    @PostMapping("/task/{id}/retry")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    @Operation(summary = "重试失败或阻塞的 AI 自动化执行用例")
    public AgentExecutionTaskDTO retry(@PathVariable String id, @RequestBody(required = false) AgentExecutionActionRequest request) {
        return agentExecutionService.retry(id, request == null ? null : request.getReason());
    }

    @GetMapping("/task/{id}/human-requests")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public List<AgentHumanRequestDTO> humanRequests(@PathVariable String id) {
        agentExecutionService.get(id);
        return humanRequestService.list(id);
    }

    @PostMapping("/task/{taskId}/human-requests/{requestId}/respond")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public AgentHumanRequestDTO respondHumanRequest(@PathVariable String taskId,
                                                    @PathVariable String requestId,
                                                    @RequestBody @Valid AgentHumanResponseRequest request) {
        agentExecutionService.get(taskId);
        agentExecutionService.respondHumanRequest(taskId, requestId, request);
        return humanRequestService.list(taskId).stream()
                .filter(item -> requestId.equals(item.getId())).findFirst().orElseThrow();
    }
}
