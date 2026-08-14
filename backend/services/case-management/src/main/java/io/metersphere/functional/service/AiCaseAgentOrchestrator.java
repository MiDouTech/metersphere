package io.metersphere.functional.service;

import io.metersphere.functional.constants.AiCaseConversationStatus;
import io.metersphere.functional.constants.AiCaseExecutionStatus;
import io.metersphere.functional.constants.AiCaseMessageRole;
import io.metersphere.functional.constants.AiCaseMessageStatus;
import io.metersphere.functional.dto.AiCaseConversationDTO;
import io.metersphere.functional.dto.AiCaseExecutionDTO;
import io.metersphere.functional.dto.AiCaseExecutionEventDTO;
import io.metersphere.functional.dto.AiCaseMessageDTO;
import io.metersphere.functional.dto.AiResourceSelection;
import io.metersphere.functional.dto.CaseGenerationCaseDTO;
import io.metersphere.functional.dto.CaseGenerationResult;
import io.metersphere.functional.repository.AiCaseAgentRepository;
import io.metersphere.functional.request.AiCaseAgentCancelRequest;
import io.metersphere.functional.request.AiCaseAgentChatRequest;
import io.metersphere.functional.request.AiCaseAgentRetryRequest;
import io.metersphere.functional.response.FunctionalCaseAiGenerateResponse;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.request.ai.AiProviderChatRequest;
import io.metersphere.system.service.ai.AiGovernanceService;
import io.metersphere.system.service.ai.provider.AiProviderAdapter;
import io.metersphere.system.dto.ai.agent.AiUserAgentConnectionDTO;
import io.metersphere.system.service.ai.agent.AgentStreamEvent;
import io.metersphere.system.service.ai.agent.UserAgentConnector;
import io.metersphere.system.service.ai.agent.UserAgentExecutionRequest;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiCaseAgentOrchestrator {
    private static final int HISTORY_MESSAGE_LIMIT = 30;
    private static final int MAX_RECOVERY_EVENTS = 2000;

    @Resource
    private AiCaseAgentRepository repository;
    @Resource
    private AiCaseConversationService conversationService;
    @Resource
    private AiCaseAvailableResourceService availableResourceService;
    @Resource
    private AiCaseAgentPromptService promptService;
    @Resource
    private AiProviderAdapter providerAdapter;
    @Resource
    private UserAgentConnector userAgentConnector;
    @Resource
    private AiGovernanceService governanceService;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Resource
    private AiCaseDocumentSearchService documentSearchService;
    @Resource
    private AiCaseDocumentContextService documentContextService;
    @Resource
    private FunctionalCaseAiDraftService draftService;

    private final Map<String, ActiveExecution> activeExecutions = new ConcurrentHashMap<>();

    public Flux<AiCaseExecutionEventDTO> chat(AiCaseAgentChatRequest request, String userId) {
        return chatInternal(request, userId, null);
    }

    public Flux<AiCaseExecutionEventDTO> retry(AiCaseAgentRetryRequest request, String userId) {
        AiCaseExecutionDTO previous = execution(request.getRequestId(), request.getProjectId(), userId);
        if (!List.of(AiCaseExecutionStatus.FAILED.name(), AiCaseExecutionStatus.CANCELED.name())
                .contains(previous.getStatus())) {
            throw new MSException("只能重试失败或已取消的 AI 执行");
        }
        AiCaseMessageDTO previousUserMessage = repository.findMessage(
                previous.getUserMessageId(), request.getProjectId(), userId);
        if (previousUserMessage == null || StringUtils.isBlank(previousUserMessage.getContent())) {
            throw new MSException("原始用户消息不存在，无法重试");
        }
        AiCaseAgentChatRequest retry = new AiCaseAgentChatRequest();
        retry.setProjectId(request.getProjectId());
        retry.setConversationId(previous.getConversationId());
        retry.setRequestId(request.getNewRequestId());
        retry.setMessage(previousUserMessage.getContent());
        if (StringUtils.isNotBlank(previous.getSourceDocumentIds())) {
            retry.setSourceDocumentIds(JSON.parseArray(previous.getSourceDocumentIds(), String.class));
        }
        return chatInternal(retry, userId, previous.getRequestId());
    }

    private Flux<AiCaseExecutionEventDTO> chatInternal(AiCaseAgentChatRequest request, String userId,
                                                        String retryOfRequestId) {
        AiCaseConversationDTO conversation = conversationService.get(
                request.getConversationId(), request.getProjectId(), userId);
        if (!AiCaseConversationStatus.ACTIVE.name().equals(conversation.getStatus())) {
            throw new MSException("归档会话不能继续发送消息");
        }
        AiResourceSelection selection = availableResourceService.requireAllowed(request.getProjectId(),
                conversation.getResourceType(), conversation.getResourceId(), conversation.getModelSourceId(), userId);
        validateRequestedResource(request, selection, userId);
        String requestId = request.getRequestId();
        if (StringUtils.isBlank(requestId)) {
            requestId = IDGenerator.nextStr();
        }
        request.setRequestId(requestId);

        AiCaseExecutionDTO existing = repository.findExecution(requestId, request.getProjectId(), userId);
        if (existing != null) {
            return existingExecutionEvents(existing);
        }

        List<AiCaseMessageDTO> history = repository.listMessages(conversation.getId(), request.getProjectId(),
                userId, null, null, HISTORY_MESSAGE_LIMIT);
        AiCaseDocumentContextService.ResolvedContext sourceContext = documentContextService.resolve(
                request.getProjectId(), request.getSourceDocumentIds());
        String providerPrompt = promptService.buildUserPrompt(history, request.getMessage())
                + sourceContext.promptContext();
        boolean toolsSupported = selection.supportsTools();
        if ("USER_AGENT".equals(selection.resourceType())) {
            toolsSupported = toolsSupported && governanceService.get(request.getProjectId()).isAllowLocalAgentTools();
        }
        boolean admittedToolsSupported = toolsSupported;
        AtomicReference<ActiveExecution> admitted = new AtomicReference<>();
        Runnable create = () -> admitted.set(createExecution(request, conversation, providerPrompt, userId,
                retryOfRequestId, selection, sourceContext.documentIds(), admittedToolsSupported));
        if ("USER_AGENT".equals(selection.resourceType())) {
            governanceService.admitAgentExecution(request.getProjectId(), userId,
                    selection.agentConnectionId(), selection.provider(), create);
        } else {
            governanceService.admitGeneration(request.getProjectId(), create);
        }
        ActiveExecution active = admitted.get();
        activeExecutions.put(requestId, active);
        active.initialEvents.forEach(active::emit);
        if ("USER_AGENT".equals(selection.resourceType())) {
            startUserAgent(active, conversation, providerPrompt, userId);
        } else {
            startProvider(active, conversation, providerPrompt, userId);
        }
        return active.sink.asFlux();
    }

    public AiCaseExecutionDTO execution(String requestId, String projectId, String userId) {
        AiCaseExecutionDTO execution = repository.findExecution(requestId, projectId, userId);
        if (execution == null) {
            throw new MSException("AI 执行不存在或无权限");
        }
        return execution;
    }

    public List<AiCaseExecutionEventDTO> events(String requestId, String projectId, String userId,
                                                 long afterSequence) {
        execution(requestId, projectId, userId);
        return repository.listEvents(requestId, Math.max(0, afterSequence), MAX_RECOVERY_EVENTS);
    }

    public void cancel(AiCaseAgentCancelRequest request, String userId) {
        AiCaseExecutionDTO execution = execution(request.getRequestId(), request.getProjectId(), userId);
        if (isTerminal(execution.getStatus())) {
            return;
        }
        repository.requestCancel(request.getRequestId(), request.getProjectId(), userId, System.currentTimeMillis());
        ActiveExecution active = activeExecutions.get(request.getRequestId());
        if (active != null) {
            active.cancelRequested.set(true);
            if ("USER_AGENT".equals(active.resourceType)) {
                userAgentConnector.cancel(active.requestId, userId);
            }
            Disposable disposable = active.subscription.get();
            if (disposable != null) {
                disposable.dispose();
            }
            finishCanceled(active);
        }
    }

    private ActiveExecution createExecution(AiCaseAgentChatRequest request, AiCaseConversationDTO conversation,
                                            String providerPrompt, String userId, String retryOfRequestId,
                                            AiResourceSelection selection, List<String> sourceDocumentIds,
                                            boolean toolsSupported) {
        ActiveExecution active = new ActiveExecution(request.getRequestId(), request.getProjectId(), userId,
                conversation.getId(), selection, providerPrompt, sourceDocumentIds, toolsSupported);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (!repository.lockConversation(conversation.getId(), request.getProjectId(), userId)) {
                throw new MSException("会话不存在或无权限");
            }
            if (repository.countActiveExecutions(conversation.getId(), request.getProjectId(), userId) > 0) {
                throw new MSException("当前会话已有运行中的 AI 请求");
            }
            long now = System.currentTimeMillis();
            AiCaseMessageDTO userMessage = message(conversation, userId, request.getRequestId(),
                    AiCaseMessageRole.USER.name(), AiCaseMessageStatus.COMPLETED.name(), request.getMessage(), now);
            AiCaseMessageDTO assistantMessage = message(conversation, userId, request.getRequestId(),
                    AiCaseMessageRole.ASSISTANT.name(), AiCaseMessageStatus.STREAMING.name(), "", now);
            assistantMessage.setModelSourceId(conversation.getModelSourceId());
            active.userMessageId = userMessage.getId();
            active.assistantMessageId = assistantMessage.getId();

            AiCaseExecutionDTO execution = new AiCaseExecutionDTO();
            execution.setId(IDGenerator.nextStr());
            execution.setRequestId(request.getRequestId());
            execution.setConversationId(conversation.getId());
            execution.setProjectId(request.getProjectId());
            execution.setUserId(userId);
            execution.setUserMessageId(userMessage.getId());
            execution.setAssistantMessageId(assistantMessage.getId());
            execution.setExecutionType("CHAT");
            execution.setStatus(AiCaseExecutionStatus.CREATED.name());
            execution.setResourceType(selection.resourceType());
            execution.setRequestedResourceId(selection.resourceId());
            execution.setActualResourceId(selection.resourceId());
            execution.setAgentConnectionId(selection.agentConnectionId());
            execution.setRequestedModelSourceId(conversation.getModelSourceId());
            execution.setCancelRequested(false);
            execution.setRetryOfRequestId(retryOfRequestId);
            execution.setSourceDocumentIds(JSON.toJSONString(sourceDocumentIds));
            execution.setTokenEstimated(true);
            execution.setCreateTime(now);
            execution.setUpdateTime(now);
            repository.insertMessage(userMessage);
            repository.insertMessage(assistantMessage);
            repository.insertExecution(execution);
            active.initialEvents.add(appendEvent(active, "execution-start", Map.of(
                    "conversationId", conversation.getId(),
                    "resourceType", selection.resourceType(),
                    "resourceId", selection.resourceId(),
                    "promptVersion", AiCaseAgentPromptService.VERSION,
                    "sourceDocumentIds", sourceDocumentIds)));
            active.initialEvents.add(appendEvent(active, "message-start", Map.of(
                    "messageId", assistantMessage.getId(), "role", "ASSISTANT")));
        });
        return active;
    }

    private void startProvider(ActiveExecution active, AiCaseConversationDTO conversation,
                               String providerPrompt, String userId) {
        long now = System.currentTimeMillis();
        active.startTime = now;
        repository.markExecutionRunning(active.requestId, active.projectId, userId, now);
        AiProviderChatRequest providerRequest = new AiProviderChatRequest();
        providerRequest.setProjectId(active.projectId);
        providerRequest.setOrganizationId(conversation.getOrganizationId());
        providerRequest.setConversationId(active.conversationId);
        providerRequest.setRequestId(active.requestId);
        providerRequest.setChatModelId(active.modelSourceId);
        providerRequest.setSystem(promptService.systemPrompt());
        providerRequest.setPrompt(providerPrompt);

        var cancelMonitor = Flux.interval(Duration.ofSeconds(1))
                .filter(ignored -> active.cancelRequested.get() || repository.isCancelRequested(active.requestId))
                .next()
                .map(ignored -> "cancel");
        try {
            List<Object> tools = active.toolsSupported ? List.of(new CaseAgentTools(active)) : List.of();
            Flux<String> providerStream = providerAdapter.streamAdmittedWithTools(
                    providerRequest, userId, tools, modelSourceId -> {
                        active.actualModelSourceId = modelSourceId;
                        repository.updateActualModel(active.requestId, active.projectId, active.userId,
                                modelSourceId, System.currentTimeMillis());
                    });
            Disposable subscription = providerStream
                    .takeUntilOther(cancelMonitor)
                    .subscribe(chunk -> onContent(active, chunk), error -> finishFailed(active, error), () -> {
                        if (active.cancelRequested.get() || repository.isCancelRequested(active.requestId)) {
                            finishCanceled(active);
                        } else {
                            finishCompleted(active);
                        }
                    });
            active.subscription.set(subscription);
            if (active.cancelRequested.get() && !subscription.isDisposed()) {
                subscription.dispose();
            }
        } catch (Throwable error) {
            finishFailed(active, error);
        }
    }

    private void validateRequestedResource(AiCaseAgentChatRequest request,
                                           AiResourceSelection conversationSelection, String userId) {
        if (StringUtils.isAllBlank(request.getResourceType(), request.getResourceId(), request.getModelSourceId())) {
            return;
        }
        AiResourceSelection requested = availableResourceService.requireAllowed(request.getProjectId(),
                request.getResourceType(), request.getResourceId(), request.getModelSourceId(), userId);
        if (!StringUtils.equals(requested.resourceType(), conversationSelection.resourceType())
                || !StringUtils.equals(requested.resourceId(), conversationSelection.resourceId())) {
            throw new MSException("聊天请求资源与当前会话资源不一致，请先切换会话资源");
        }
    }

    private void startUserAgent(ActiveExecution active, AiCaseConversationDTO conversation,
                                String providerPrompt, String userId) {
        long now = System.currentTimeMillis();
        active.startTime = now;
        repository.markExecutionRunning(active.requestId, active.projectId, userId, now);
        try {
            AiUserAgentConnectionDTO connection = userAgentConnector.connectionStatus(
                    active.agentConnectionId, userId);
            active.agentDeviceId = connection.getDeviceId();
            repository.updateExecutionAgentDevice(active.requestId, active.projectId, active.userId,
                    active.agentDeviceId, System.currentTimeMillis());
            int maxExecutionMinutes = Math.max(1,
                    governanceService.get(active.projectId).getMaxAgentExecutionMinutes());
            UserAgentExecutionRequest bridgeRequest = new UserAgentExecutionRequest(
                    active.requestId, active.conversationId, active.projectId, active.agentConnectionId,
                    connection.getDeviceId(), connection.getProvider(),
                    promptService.bridgeSystemPrompt(active.toolsSupported),
                    providerPrompt, null, active.toolsSupported
                    ? List.of("search_product_documents", "create_case_drafts") : List.of(),
                    Map.of("maxToolCalls", 8, "maxWriteToolCalls", 3,
                            "maxExecutionSeconds", maxExecutionMinutes * 60));
            Disposable subscription = userAgentConnector.stream(bridgeRequest, userId)
                    .timeout(Duration.ofMinutes(maxExecutionMinutes).plusSeconds(5))
                    .subscribe(event -> onAgentEvent(active, event), error -> finishFailed(active, error), () -> {
                        if (!active.terminal.get()) {
                            if (active.cancelRequested.get() || repository.isCancelRequested(active.requestId)) {
                                finishCanceled(active);
                            } else {
                                finishCompleted(active);
                            }
                        }
                    });
            active.subscription.set(subscription);
        } catch (Throwable error) {
            finishFailed(active, error);
        }
    }

    @SuppressWarnings("unchecked")
    private void onAgentEvent(ActiveExecution active, AgentStreamEvent event) {
        if (active.terminal.get() || active.cancelRequested.get()) {
            if ("tool.call".equals(event.type())) {
                Object value = event.payload() == null ? null : event.payload().get("toolCallId");
                String toolCallId = StringUtils.left(value instanceof String id ? id : "", 128);
                userAgentConnector.sendToolResult(active.requestId, toolCallId, false,
                        Map.of(), "AI_AGENT_EXECUTION_TERMINATED");
            }
            return;
        }
        switch (event.type()) {
            case "content.delta" -> onContent(active, StringUtils.defaultString(
                    (String) event.payload().get("delta")));
            case "usage.reported" -> {
                active.inputTokens = number(event.payload().get("inputTokens"));
                active.outputTokens = number(event.payload().get("outputTokens"));
                active.usageEstimated = !Boolean.FALSE.equals(event.payload().get("estimated"));
            }
            case "tool.call" -> handleAgentToolCall(active, event.payload());
            case "execution.accepted" -> {
                String externalSessionId = StringUtils.left(
                        (String) event.payload().get("externalSessionId"), 255);
                repository.upsertAgentSessionBinding(active.conversationId, active.agentConnectionId,
                        externalSessionId, active.provider, active.agentDeviceId, event.sequence(),
                        System.currentTimeMillis());
                active.emit(appendEvent(active, "agent-accepted", Map.of(
                        "deviceId", StringUtils.defaultString(active.agentDeviceId),
                        "externalSessionId", StringUtils.defaultString(externalSessionId))));
            }
            case "message.start" -> active.emit(appendEvent(active, "agent-message-start", event.payload()));
            default -> {
                // Terminal events are converted by stream completion; unknown optional events are ignored.
            }
        }
    }

    private void handleAgentToolCall(ActiveExecution active, Map<String, Object> payload) {
        String toolCallId = StringUtils.left((String) payload.get("toolCallId"), 128);
        String toolName = StringUtils.left((String) payload.get("toolName"), 128);
        if (!active.toolsSupported || StringUtils.isAnyBlank(toolCallId, toolName)
                || !List.of("search_product_documents", "create_case_drafts").contains(toolName)) {
            userAgentConnector.sendToolResult(active.requestId, StringUtils.defaultString(toolCallId), false,
                    Map.of(), "AI_AGENT_TOOL_NOT_ALLOWED");
            return;
        }
        try {
            CaseAgentTools tools = new CaseAgentTools(active);
            String result;
            Map<String, Object> arguments = payload.get("arguments") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map : Map.of();
            if (StringUtils.equals(toolName, "search_product_documents")) {
                Number maxResults = arguments.get("maxResults") instanceof Number number ? number : null;
                result = tools.searchProductDocuments((String) arguments.get("query"),
                        maxResults == null ? null : maxResults.intValue());
            } else {
                List<CaseGenerationCaseDTO> cases = JSON.parseObject(JSON.toJSONString(
                        arguments.getOrDefault("cases", List.of())),
                        new com.fasterxml.jackson.core.type.TypeReference<List<CaseGenerationCaseDTO>>() { });
                result = tools.createCaseDrafts(cases);
            }
            userAgentConnector.sendToolResult(active.requestId, toolCallId, true,
                    Map.of("content", result), null);
        } catch (Exception error) {
            userAgentConnector.sendToolResult(active.requestId, toolCallId, false, Map.of(),
                    "AI_AGENT_TOOL_EXECUTION_FAILED");
        }
    }

    private long number(Object value) {
        return value instanceof Number number ? Math.max(0, number.longValue()) : 0;
    }

    private void onContent(ActiveExecution active, String chunk) {
        if (active.terminal.get() || active.cancelRequested.get() || StringUtils.isEmpty(chunk)) {
            return;
        }
        if (active.content.isEmpty()) {
            repository.markFirstToken(active.requestId, System.currentTimeMillis());
        }
        active.content.append(chunk);
        active.emit(appendEvent(active, "content-delta", Map.of(
                "messageId", active.assistantMessageId, "delta", chunk)));
    }

    private void finishCompleted(ActiveExecution active) {
        if ("USER_AGENT".equals(active.resourceType) && active.writeToolInvocationCount.get() == 0) {
            try {
                materializeStructuredAgentDrafts(active);
            } catch (Exception error) {
                finishFailed(active, error);
                return;
            }
        }
        if (!active.terminal.compareAndSet(false, true)) {
            return;
        }
        long finishTime = System.currentTimeMillis();
        long inputTokens = active.resourceType.equals("USER_AGENT") && active.inputTokens > 0
                ? active.inputTokens : estimateTokens(active.providerPrompt);
        long outputTokens = active.resourceType.equals("USER_AGENT") && active.outputTokens > 0
                ? active.outputTokens : estimateTokens(active.content.toString());
        boolean estimated = !active.resourceType.equals("USER_AGENT") || active.usageEstimated;
        repository.completeMessage(active.assistantMessageId, active.projectId, active.userId,
                AiCaseMessageStatus.COMPLETED.name(), active.content.toString(), active.actualModelSourceId,
                inputTokens, outputTokens, estimated, null, finishTime);
        repository.completeExecution(active.requestId, active.projectId, active.userId,
                AiCaseExecutionStatus.COMPLETED.name(), inputTokens, outputTokens, estimated,
                null, null, finishTime, finishTime - active.startTime);
        active.emit(appendEvent(active, "usage", Map.of(
                "inputTokens", inputTokens, "outputTokens", outputTokens,
                "totalTokens", inputTokens + outputTokens, "estimated", estimated)));
        recordAgentUsage(active, "COMPLETED", null, finishTime);
        active.emit(appendEvent(active, "message-completed", Map.of(
                "messageId", active.assistantMessageId, "content", active.content.toString())));
        active.emit(appendEvent(active, "execution-completed", Map.of("status", "COMPLETED")));
        close(active);
    }

    @SuppressWarnings("unchecked")
    private void materializeStructuredAgentDrafts(ActiveExecution active) {
        String raw = StringUtils.trim(active.content.toString());
        if (!raw.contains("\"cases\"")) {
            return;
        }
        CaseGenerationResult generationResult;
        try {
            generationResult = draftService.parseAgentGenerationResult(raw);
        } catch (RuntimeException error) {
            throw new MSException("AI_AGENT_STRUCTURED_OUTPUT_INVALID：Agent 返回的用例结构无法解析或修复");
        }
        List<CaseGenerationCaseDTO> cases = generationResult.getCases();
        if (cases.isEmpty()) {
            return;
        }
        new CaseAgentTools(active).createCaseDrafts(cases);
        String reply = extractStructuredReply(raw);
        active.content.setLength(0);
        active.content.append(StringUtils.defaultIfBlank(reply, "已生成通过平台校验的用例草稿，请确认后再保存为正式用例。"));
    }

    @SuppressWarnings("unchecked")
    private String extractStructuredReply(String raw) {
        int objectStart = raw.indexOf('{');
        int objectEnd = raw.lastIndexOf('}');
        if (objectStart < 0 || objectEnd <= objectStart) {
            return null;
        }
        try {
            Map<String, Object> response = JSON.parseObject(raw.substring(objectStart, objectEnd + 1), Map.class);
            return response.get("reply") instanceof String value ? StringUtils.trimToNull(value) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void recordAgentUsage(ActiveExecution active, String status, String errorCode, long finishTime) {
        if ("USER_AGENT".equals(active.resourceType)) {
            governanceService.recordAgentUsage(active.projectId, active.userId, active.conversationId,
                    active.requestId, active.agentConnectionId, active.agentDeviceId, active.provider,
                    Math.max(0, finishTime - active.startTime), active.inputTokens, active.outputTokens,
                    active.usageEstimated, status, errorCode);
        }
    }

    private void finishCanceled(ActiveExecution active) {
        if (!active.terminal.compareAndSet(false, true)) {
            return;
        }
        long finishTime = System.currentTimeMillis();
        long inputTokens = estimateTokens(active.providerPrompt);
        long outputTokens = estimateTokens(active.content.toString());
        repository.completeMessage(active.assistantMessageId, active.projectId, active.userId,
                AiCaseMessageStatus.CANCELED.name(), active.content.toString(), active.actualModelSourceId,
                inputTokens, outputTokens, true, "AI_AGENT_CANCELED", finishTime);
        repository.completeExecution(active.requestId, active.projectId, active.userId,
                AiCaseExecutionStatus.CANCELED.name(), inputTokens, outputTokens, true,
                "AI_AGENT_CANCELED", "用户取消执行", finishTime, finishTime - active.startTime);
        recordAgentUsage(active, "CANCELED", "AI_AGENT_CANCELED", finishTime);
        active.emit(appendEvent(active, "execution-completed", Map.of("status", "CANCELED")));
        close(active);
    }

    private void finishFailed(ActiveExecution active, Throwable error) {
        if (error instanceof CancellationException || active.cancelRequested.get()) {
            finishCanceled(active);
            return;
        }
        if (!active.terminal.compareAndSet(false, true)) {
            return;
        }
        long finishTime = System.currentTimeMillis();
        String message = sanitizeError(error);
        repository.completeMessage(active.assistantMessageId, active.projectId, active.userId,
                AiCaseMessageStatus.FAILED.name(), active.content.toString(), active.actualModelSourceId,
                estimateTokens(active.providerPrompt), estimateTokens(active.content.toString()), true,
                "AI_AGENT_PROVIDER_ERROR", finishTime);
        repository.completeExecution(active.requestId, active.projectId, active.userId,
                AiCaseExecutionStatus.FAILED.name(), estimateTokens(active.providerPrompt),
                estimateTokens(active.content.toString()), true, "AI_AGENT_PROVIDER_ERROR", message,
                finishTime, finishTime - active.startTime);
        recordAgentUsage(active, "FAILED", "AI_AGENT_PROVIDER_ERROR", finishTime);
        active.emit(appendEvent(active, "error", Map.of(
                "errorCode", "AI_AGENT_PROVIDER_ERROR", "message", message, "retryable", true)));
        active.emit(appendEvent(active, "execution-completed", Map.of("status", "FAILED")));
        close(active);
    }

    private AiCaseMessageDTO message(AiCaseConversationDTO conversation, String userId, String requestId,
                                     String role, String status, String content, long now) {
        AiCaseMessageDTO message = new AiCaseMessageDTO();
        message.setId(IDGenerator.nextStr());
        message.setConversationId(conversation.getId());
        message.setProjectId(conversation.getProjectId());
        message.setUserId(userId);
        message.setRole(role);
        message.setStatus(status);
        message.setResourceType(conversation.getResourceType());
        message.setResourceId(conversation.getResourceId());
        message.setAgentConnectionId(conversation.getAgentConnectionId());
        message.setContent(content);
        message.setRequestId(requestId);
        message.setTokenEstimated(false);
        message.setCreateTime(now);
        message.setUpdateTime(now);
        return message;
    }

    private AiCaseExecutionEventDTO appendEvent(ActiveExecution active, String eventType, Map<String, ?> payload) {
        return repository.appendEvent(active.requestId, eventType, JSON.toJSONString(payload),
                System.currentTimeMillis());
    }

    private Flux<AiCaseExecutionEventDTO> existingExecutionEvents(AiCaseExecutionDTO execution) {
        ActiveExecution active = activeExecutions.get(execution.getRequestId());
        if (active != null) {
            return active.sink.asFlux();
        }
        return Flux.fromIterable(repository.listEvents(execution.getRequestId(), 0, MAX_RECOVERY_EVENTS));
    }

    private boolean isTerminal(String status) {
        return List.of(AiCaseExecutionStatus.COMPLETED.name(), AiCaseExecutionStatus.FAILED.name(),
                AiCaseExecutionStatus.CANCELED.name()).contains(status);
    }

    private long estimateTokens(String value) {
        return StringUtils.isEmpty(value) ? 0 : Math.max(1, (value.length() + 3L) / 4L);
    }

    private String sanitizeError(Throwable error) {
        String message = StringUtils.defaultIfBlank(error == null ? null : error.getMessage(), "AI Provider 调用失败");
        message = message.replaceAll("(?i)(api[_-]?key|authorization|token|secret)\\s*[=:]\\s*[^\\s,;]+", "$1=******");
        return StringUtils.left(message, 1000);
    }

    private void close(ActiveExecution active) {
        active.sink.tryEmitComplete();
        activeExecutions.remove(active.requestId, active);
    }

    private static class ActiveExecution {
        private final String requestId;
        private final String projectId;
        private final String userId;
        private final String conversationId;
        private final String modelSourceId;
        private final String resourceType;
        private final String resourceId;
        private final String agentConnectionId;
        private final String provider;
        private volatile String agentDeviceId;
        private volatile String actualModelSourceId;
        private final String providerPrompt;
        private final List<String> sourceDocumentIds;
        private final boolean toolsSupported;
        private final StringBuilder content = new StringBuilder();
        private final List<AiCaseExecutionEventDTO> initialEvents = new ArrayList<>();
        private final Sinks.Many<AiCaseExecutionEventDTO> sink = Sinks.many().replay().limit(512);
        private final AtomicReference<Disposable> subscription = new AtomicReference<>();
        private final AtomicBoolean cancelRequested = new AtomicBoolean();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicInteger toolInvocationCount = new AtomicInteger();
        private final AtomicInteger writeToolInvocationCount = new AtomicInteger();
        private String userMessageId;
        private String assistantMessageId;
        private long startTime;
        private long inputTokens;
        private long outputTokens;
        private boolean usageEstimated = true;

        private ActiveExecution(String requestId, String projectId, String userId, String conversationId,
                                AiResourceSelection selection, String providerPrompt,
                                List<String> sourceDocumentIds, boolean toolsSupported) {
            this.requestId = requestId;
            this.projectId = projectId;
            this.userId = userId;
            this.conversationId = conversationId;
            this.modelSourceId = selection.modelSourceId();
            this.actualModelSourceId = selection.modelSourceId();
            this.resourceType = selection.resourceType();
            this.resourceId = selection.resourceId();
            this.agentConnectionId = selection.agentConnectionId();
            this.provider = selection.provider();
            this.providerPrompt = providerPrompt;
            this.sourceDocumentIds = List.copyOf(sourceDocumentIds);
            this.toolsSupported = toolsSupported;
        }

        private void emit(AiCaseExecutionEventDTO event) {
            if (event != null) {
                sink.tryEmitNext(event);
            }
        }
    }

    private class CaseAgentTools {
        private final ActiveExecution active;

        private CaseAgentTools(ActiveExecution active) {
            this.active = active;
        }

        @Tool(name = "search_product_documents", description = "检索当前项目和会话中已解析的产品方案，返回可引用的文档片段。")
        public String searchProductDocuments(
                @ToolParam(description = "要检索的功能、业务规则或关键词") String query,
                @ToolParam(description = "最多返回片段数，范围 1-10", required = false) Integer maxResults) {
            if (active.terminal.get() || active.cancelRequested.get()
                    || repository.isCancelRequested(active.requestId)) {
                throw new CancellationException("AI 执行已取消");
            }
            assertToolBudget(false);
            String toolCallId = IDGenerator.nextStr();
            String arguments = JSON.toJSONString(Map.of(
                    "query", StringUtils.left(query, 500),
                    "maxResults", maxResults == null ? 5 : maxResults));
            active.emit(appendEvent(active, "tool-call", Map.of(
                    "toolCallId", toolCallId, "toolName", "search_product_documents",
                    "arguments", arguments)));
            try {
                String result = documentSearchService.search(active.projectId, active.conversationId,
                        active.userId, active.sourceDocumentIds, query, maxResults);
                long now = System.currentTimeMillis();
                AiCaseMessageDTO toolMessage = new AiCaseMessageDTO();
                toolMessage.setId(IDGenerator.nextStr());
                toolMessage.setConversationId(active.conversationId);
                toolMessage.setProjectId(active.projectId);
                toolMessage.setUserId(active.userId);
                toolMessage.setRole(AiCaseMessageRole.TOOL.name());
                toolMessage.setStatus(AiCaseMessageStatus.COMPLETED.name());
                toolMessage.setResourceType(active.resourceType);
                toolMessage.setResourceId(active.resourceId);
                toolMessage.setAgentConnectionId(active.agentConnectionId);
                toolMessage.setContent(result);
                toolMessage.setRequestId(active.requestId);
                toolMessage.setToolName("search_product_documents");
                toolMessage.setToolCallId(toolCallId);
                toolMessage.setToolArguments(arguments);
                toolMessage.setToolResult(result);
                toolMessage.setTokenEstimated(false);
                toolMessage.setCreateTime(now);
                toolMessage.setUpdateTime(now);
                repository.insertMessage(toolMessage);
                active.emit(appendEvent(active, "tool-result", Map.of(
                        "toolCallId", toolCallId, "toolName", "search_product_documents",
                        "result", result, "success", true)));
                return result;
            } catch (Exception error) {
                active.emit(appendEvent(active, "tool-result", Map.of(
                        "toolCallId", toolCallId, "toolName", "search_product_documents",
                        "errorCode", "AI_AGENT_DOCUMENT_SEARCH_FAILED", "success", false)));
                throw error;
            }
        }

        @Tool(name = "create_case_drafts", description = "在当前会话创建功能测试用例草稿。仅创建草稿，不会保存为正式用例。")
        public String createCaseDrafts(
                @ToolParam(description = "草稿列表。每项必须包含 name、level(P0-P3)、editType(STEP/TEXT)；STEP 需提供 steps，TEXT 需提供 textDescription。")
                List<CaseGenerationCaseDTO> cases) {
            if (active.terminal.get() || active.cancelRequested.get()
                    || repository.isCancelRequested(active.requestId)) {
                throw new CancellationException("AI 执行已取消");
            }
            assertToolBudget(true);
            List<CaseGenerationCaseDTO> requestedCases = cases == null ? List.of() : cases;
            String arguments = JSON.toJSONString(Map.of("cases", requestedCases));
            String argumentsHash = DigestUtils.sha256Hex(arguments);
            String toolCallId = "create_" + DigestUtils.sha256Hex(active.requestId + ":" + arguments).substring(0, 32);
            String succeeded = repository.findSucceededToolResult(active.conversationId, toolCallId,
                    active.projectId, active.userId);
            if (succeeded != null) {
                return succeeded;
            }
            long now = System.currentTimeMillis();
            int inserted = repository.insertToolCall(IDGenerator.nextStr(), active.requestId,
                    active.conversationId, active.projectId, active.userId, toolCallId,
                    "create_case_drafts", argumentsHash, arguments, false, now);
            if (inserted == 0) {
                throw new MSException("相同草稿工具调用正在处理或已经失败，请勿重复提交");
            }
            active.emit(appendEvent(active, "tool-call", Map.of(
                    "toolCallId", toolCallId, "toolName", "create_case_drafts",
                    "argumentsHash", argumentsHash)));
            try {
                FunctionalCaseAiGenerateResponse response = draftService.createDraftsFromAgent(
                        active.projectId, active.conversationId, active.requestId, active.modelSourceId,
                        requestedCases, active.userId);
                String result = JSON.toJSONString(Map.of(
                        "generationId", response.getGenerationId(),
                        "createdCount", response.getCreatedCount(),
                        "createdIds", response.getDrafts().stream().map(item -> item.getId()).toList(),
                        "warnings", response.getWarnings()));
                repository.completeToolCall(active.conversationId, toolCallId, active.projectId,
                        active.userId, "SUCCEEDED", result, null, System.currentTimeMillis());
                insertToolMessage(active, toolCallId, "create_case_drafts", arguments, result,
                        AiCaseMessageStatus.COMPLETED.name());
                active.emit(appendEvent(active, "tool-result", Map.of(
                        "toolCallId", toolCallId, "toolName", "create_case_drafts",
                        "result", result, "success", true)));
                active.emit(appendEvent(active, "drafts-changed", Map.of(
                        "createdIds", response.getDrafts().stream().map(item -> item.getId()).toList(),
                        "createdCount", response.getCreatedCount())));
                return result;
            } catch (Exception error) {
                repository.completeToolCall(active.conversationId, toolCallId, active.projectId,
                        active.userId, "FAILED", null, "AI_AGENT_DRAFT_CREATE_FAILED", System.currentTimeMillis());
                active.emit(appendEvent(active, "tool-result", Map.of(
                        "toolCallId", toolCallId, "toolName", "create_case_drafts",
                        "errorCode", "AI_AGENT_DRAFT_CREATE_FAILED", "success", false)));
                throw error;
            }
        }

        private void insertToolMessage(ActiveExecution active, String toolCallId, String toolName,
                                       String arguments, String result, String status) {
            long now = System.currentTimeMillis();
            AiCaseMessageDTO toolMessage = new AiCaseMessageDTO();
            toolMessage.setId(IDGenerator.nextStr());
            toolMessage.setConversationId(active.conversationId);
            toolMessage.setProjectId(active.projectId);
            toolMessage.setUserId(active.userId);
            toolMessage.setRole(AiCaseMessageRole.TOOL.name());
            toolMessage.setStatus(status);
            toolMessage.setResourceType(active.resourceType);
            toolMessage.setResourceId(active.resourceId);
            toolMessage.setAgentConnectionId(active.agentConnectionId);
            toolMessage.setContent(result);
            toolMessage.setRequestId(active.requestId);
            toolMessage.setToolName(toolName);
            toolMessage.setToolCallId(toolCallId);
            toolMessage.setToolArguments(arguments);
            toolMessage.setToolResult(result);
            toolMessage.setTokenEstimated(false);
            toolMessage.setCreateTime(now);
            toolMessage.setUpdateTime(now);
            repository.insertMessage(toolMessage);
        }

        private void assertToolBudget(boolean writeTool) {
            if (active.toolInvocationCount.incrementAndGet() > 8) {
                throw new MSException("AI_AGENT_TOOL_ROUND_LIMIT：单次执行最多调用 8 次工具");
            }
            if (writeTool && active.writeToolInvocationCount.incrementAndGet() > 3) {
                throw new MSException("AI_AGENT_WRITE_TOOL_LIMIT：单次执行最多调用 3 次写工具");
            }
        }
    }
}
