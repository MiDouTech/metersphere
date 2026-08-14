package io.metersphere.functional.controller;

import io.metersphere.functional.dto.AiCaseConversationDTO;
import io.metersphere.functional.dto.AiCaseConversationPageResponse;
import io.metersphere.functional.dto.AiCaseExecutionDTO;
import io.metersphere.functional.dto.AiCaseExecutionEventDTO;
import io.metersphere.functional.dto.AiCaseMessagePageResponse;
import io.metersphere.functional.dto.AiSelectableResourceDTO;
import io.metersphere.functional.request.AiCaseAgentCancelRequest;
import io.metersphere.functional.request.AiCaseAgentChatRequest;
import io.metersphere.functional.request.AiCaseAgentRetryRequest;
import io.metersphere.functional.request.AiCaseConversationCreateRequest;
import io.metersphere.functional.request.AiCaseConversationModelRequest;
import io.metersphere.functional.request.AiCaseConversationOperationRequest;
import io.metersphere.functional.request.AiCaseConversationPageRequest;
import io.metersphere.functional.request.AiCaseConversationRenameRequest;
import io.metersphere.functional.request.AiCaseMessagePageRequest;
import io.metersphere.functional.service.AiCaseAvailableResourceService;
import io.metersphere.functional.service.AiCaseAgentOrchestrator;
import io.metersphere.functional.service.AiCaseConversationService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.security.CheckOwner;
import io.metersphere.system.utils.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/functional/case/ai/agent")
@Tag(name = "用例管理-功能用例-AI Agent 会话")
public class AiCaseAgentConversationController {
    @Resource
    private AiCaseConversationService conversationService;
    @Resource
    private AiCaseAvailableResourceService availableResourceService;
    @Resource
    private AiCaseAgentOrchestrator agentOrchestrator;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "向用例 Agent 发送消息并订阅执行事件")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public Flux<ServerSentEvent<String>> chat(@Validated @RequestBody AiCaseAgentChatRequest request) {
        return agentOrchestrator.chat(request, SessionUtils.getUserId()).map(this::toServerSentEvent);
    }

    @PostMapping("/chat/cancel")
    @Operation(summary = "停止用例 Agent 执行")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public void cancel(@Validated @RequestBody AiCaseAgentCancelRequest request) {
        agentOrchestrator.cancel(request, SessionUtils.getUserId());
    }

    @PostMapping(value = "/chat/retry", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "重试失败或已取消的用例 Agent 执行")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public Flux<ServerSentEvent<String>> retry(@Validated @RequestBody AiCaseAgentRetryRequest request) {
        return agentOrchestrator.retry(request, SessionUtils.getUserId()).map(this::toServerSentEvent);
    }

    @GetMapping("/execution/{requestId}")
    @Operation(summary = "查询用例 Agent 执行状态")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_READ)
    @CheckOwner(resourceId = "#projectId", resourceType = "project")
    public AiCaseExecutionDTO execution(@PathVariable String requestId, @RequestParam String projectId) {
        return agentOrchestrator.execution(requestId, projectId, SessionUtils.getUserId());
    }

    @GetMapping("/execution/{requestId}/events")
    @Operation(summary = "按序号恢复用例 Agent 执行事件")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_READ)
    @CheckOwner(resourceId = "#projectId", resourceType = "project")
    public List<AiCaseExecutionEventDTO> events(@PathVariable String requestId,
                                                @RequestParam String projectId,
                                                @RequestParam(defaultValue = "0") long afterSequence) {
        return agentOrchestrator.events(requestId, projectId, SessionUtils.getUserId(), afterSequence);
    }

    @GetMapping("/resources")
    @Operation(summary = "查询当前项目可用的模型 API 与个人 Agent 资源")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_READ)
    @CheckOwner(resourceId = "#projectId", resourceType = "project")
    public List<AiSelectableResourceDTO> resources(@RequestParam String projectId) {
        return availableResourceService.list(projectId, SessionUtils.getUserId());
    }

    @PostMapping("/conversation/create")
    @Operation(summary = "创建用例 Agent 会话")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public AiCaseConversationDTO create(@Validated @RequestBody AiCaseConversationCreateRequest request) {
        return conversationService.create(request, SessionUtils.getUserId());
    }

    @PostMapping("/conversation/page")
    @Operation(summary = "分页查询当前用户的用例 Agent 会话")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_READ)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public AiCaseConversationPageResponse page(@Validated @RequestBody AiCaseConversationPageRequest request) {
        return conversationService.page(request, SessionUtils.getUserId());
    }

    @GetMapping("/conversation/{id}")
    @Operation(summary = "查询用例 Agent 会话")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_READ)
    @CheckOwner(resourceId = "#projectId", resourceType = "project")
    public AiCaseConversationDTO get(@PathVariable String id, @RequestParam String projectId) {
        return conversationService.get(id, projectId, SessionUtils.getUserId());
    }

    @PostMapping("/conversation/messages")
    @Operation(summary = "游标分页查询用例 Agent 消息")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_READ)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public AiCaseMessagePageResponse messages(@Validated @RequestBody AiCaseMessagePageRequest request) {
        return conversationService.messages(request, SessionUtils.getUserId());
    }

    @PostMapping("/conversation/rename")
    @Operation(summary = "重命名用例 Agent 会话")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public AiCaseConversationDTO rename(@Validated @RequestBody AiCaseConversationRenameRequest request) {
        return conversationService.rename(request, SessionUtils.getUserId());
    }

    @PostMapping("/conversation/resource")
    @Operation(summary = "切换用例 Agent 会话 AI 资源")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public AiCaseConversationDTO switchResource(@Validated @RequestBody AiCaseConversationModelRequest request) {
        return conversationService.switchModel(request, SessionUtils.getUserId());
    }

    @PostMapping("/conversation/archive")
    @Operation(summary = "归档用例 Agent 会话")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public AiCaseConversationDTO archive(@Validated @RequestBody AiCaseConversationOperationRequest request) {
        return conversationService.archive(request, SessionUtils.getUserId());
    }

    @PostMapping("/conversation/delete")
    @Operation(summary = "删除用例 Agent 会话")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public void delete(@Validated @RequestBody AiCaseConversationOperationRequest request) {
        conversationService.delete(request, SessionUtils.getUserId());
    }

    private ServerSentEvent<String> toServerSentEvent(AiCaseExecutionEventDTO event) {
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(event.getSequence()))
                .event(event.getEventType())
                .data(JSON.toJSONString(event))
                .build();
    }
}
