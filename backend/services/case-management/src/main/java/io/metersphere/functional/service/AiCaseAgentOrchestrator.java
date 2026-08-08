package io.metersphere.functional.service;

import io.metersphere.functional.constants.AiCaseConversationStatus;
import io.metersphere.functional.constants.AiCaseExecutionStatus;
import io.metersphere.functional.constants.AiCaseMessageRole;
import io.metersphere.functional.constants.AiCaseMessageStatus;
import io.metersphere.functional.dto.AiCaseConversationDTO;
import io.metersphere.functional.dto.AiCaseAvailableModelDTO;
import io.metersphere.functional.dto.AiCaseExecutionDTO;
import io.metersphere.functional.dto.AiCaseExecutionEventDTO;
import io.metersphere.functional.dto.AiCaseMessageDTO;
import io.metersphere.functional.dto.CaseGenerationCaseDTO;
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
    private AiCaseAvailableModelService availableModelService;
    @Resource
    private AiCaseAgentPromptService promptService;
    @Resource
    private AiProviderAdapter providerAdapter;
    @Resource
    private AiGovernanceService governanceService;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Resource
    private AiCaseDocumentSearchService documentSearchService;
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
        return chatInternal(retry, userId, previous.getRequestId());
    }

    private Flux<AiCaseExecutionEventDTO> chatInternal(AiCaseAgentChatRequest request, String userId,
                                                        String retryOfRequestId) {
        AiCaseConversationDTO conversation = conversationService.get(
                request.getConversationId(), request.getProjectId(), userId);
        if (!AiCaseConversationStatus.ACTIVE.name().equals(conversation.getStatus())) {
            throw new MSException("归档会话不能继续发送消息");
        }
        AiCaseAvailableModelDTO selectedModel = availableModelService.requireAllowed(
                request.getProjectId(), conversation.getModelSourceId(), userId);
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
        String providerPrompt = promptService.buildUserPrompt(history, request.getMessage());
        AtomicReference<ActiveExecution> admitted = new AtomicReference<>();
        governanceService.admitGeneration(request.getProjectId(),
                () -> admitted.set(createExecution(request, conversation, providerPrompt, userId,
                        retryOfRequestId, selectedModel.isSupportsTools())));
        ActiveExecution active = admitted.get();
        activeExecutions.put(requestId, active);
        active.initialEvents.forEach(active::emit);
        startProvider(active, conversation, providerPrompt, userId);
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
            Disposable disposable = active.subscription.get();
            if (disposable != null) {
                disposable.dispose();
            }
            finishCanceled(active);
        }
    }

    private ActiveExecution createExecution(AiCaseAgentChatRequest request, AiCaseConversationDTO conversation,
                                            String providerPrompt, String userId, String retryOfRequestId,
                                            boolean toolsSupported) {
        ActiveExecution active = new ActiveExecution(request.getRequestId(), request.getProjectId(), userId,
                conversation.getId(), conversation.getModelSourceId(), providerPrompt, toolsSupported);
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
            execution.setRequestedModelSourceId(conversation.getModelSourceId());
            execution.setCancelRequested(false);
            execution.setRetryOfRequestId(retryOfRequestId);
            execution.setTokenEstimated(true);
            execution.setCreateTime(now);
            execution.setUpdateTime(now);
            repository.insertMessage(userMessage);
            repository.insertMessage(assistantMessage);
            repository.insertExecution(execution);
            active.initialEvents.add(appendEvent(active, "execution-start", Map.of(
                    "conversationId", conversation.getId(),
                    "modelSourceId", conversation.getModelSourceId(),
                    "promptVersion", AiCaseAgentPromptService.VERSION)));
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
        if (!active.terminal.compareAndSet(false, true)) {
            return;
        }
        long finishTime = System.currentTimeMillis();
        long inputTokens = estimateTokens(active.providerPrompt);
        long outputTokens = estimateTokens(active.content.toString());
        repository.completeMessage(active.assistantMessageId, active.projectId, active.userId,
                AiCaseMessageStatus.COMPLETED.name(), active.content.toString(), active.actualModelSourceId,
                inputTokens, outputTokens, true, null, finishTime);
        repository.completeExecution(active.requestId, active.projectId, active.userId,
                AiCaseExecutionStatus.COMPLETED.name(), inputTokens, outputTokens, true,
                null, null, finishTime, finishTime - active.startTime);
        active.emit(appendEvent(active, "usage", Map.of(
                "inputTokens", inputTokens, "outputTokens", outputTokens,
                "totalTokens", inputTokens + outputTokens, "estimated", true)));
        active.emit(appendEvent(active, "message-completed", Map.of(
                "messageId", active.assistantMessageId, "content", active.content.toString())));
        active.emit(appendEvent(active, "execution-completed", Map.of("status", "COMPLETED")));
        close(active);
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
        private volatile String actualModelSourceId;
        private final String providerPrompt;
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

        private ActiveExecution(String requestId, String projectId, String userId, String conversationId,
                                String modelSourceId, String providerPrompt, boolean toolsSupported) {
            this.requestId = requestId;
            this.projectId = projectId;
            this.userId = userId;
            this.conversationId = conversationId;
            this.modelSourceId = modelSourceId;
            this.actualModelSourceId = modelSourceId;
            this.providerPrompt = providerPrompt;
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
                        active.userId, query, maxResults);
                long now = System.currentTimeMillis();
                AiCaseMessageDTO toolMessage = new AiCaseMessageDTO();
                toolMessage.setId(IDGenerator.nextStr());
                toolMessage.setConversationId(active.conversationId);
                toolMessage.setProjectId(active.projectId);
                toolMessage.setUserId(active.userId);
                toolMessage.setRole(AiCaseMessageRole.TOOL.name());
                toolMessage.setStatus(AiCaseMessageStatus.COMPLETED.name());
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
