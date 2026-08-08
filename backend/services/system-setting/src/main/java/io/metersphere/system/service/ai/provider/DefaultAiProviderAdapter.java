package io.metersphere.system.service.ai.provider;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.request.ai.AIChatOption;
import io.metersphere.system.dto.request.ai.AIChatRequest;
import io.metersphere.system.dto.request.ai.AiModelSourceDTO;
import io.metersphere.system.dto.request.ai.AiProviderCapabilityDTO;
import io.metersphere.system.dto.request.ai.AiProviderTestRequest;
import io.metersphere.system.dto.request.ai.AiProviderTestResponse;
import io.metersphere.system.dto.request.ai.AiProviderChatRequest;
import io.metersphere.system.dto.request.ai.AiProviderInvocationResult;
import io.metersphere.system.service.AiChatBaseService;
import io.metersphere.system.service.SystemAIConfigService;
import io.metersphere.system.service.ai.AiGovernanceService;
import io.metersphere.system.service.ai.AiAuditService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class DefaultAiProviderAdapter implements AiProviderAdapter {
    private static final int MAX_ATTEMPTS = 3;
    private static final int RATE_LIMIT_PER_MINUTE = 30;
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();
    @Resource
    private SystemAIConfigService systemAIConfigService;
    @Resource
    private AiChatBaseService aiChatBaseService;
    @Resource
    private AiGovernanceService aiGovernanceService;
    @Resource
    private AiAuditService aiAuditService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public AiProviderCapabilityDTO capability(String modelSourceId, String userId) {
        AiModelSourceDTO model = systemAIConfigService.getModelSourceDTOWithKey(modelSourceId, userId);
        AiProviderCapabilityDTO capability = new AiProviderCapabilityDTO();
        capability.setModelSourceId(model.getId());
        capability.setProviderName(model.getProviderName());
        capability.setBaseName(model.getBaseName());
        capability.setStreamSupported(true);
        capability.setOauthSupported(false);
        capability.setAgentGatewaySupported(false);
        capability.setFeatures(List.of("CHAT_COMPLETION", "CASE_GENERATION"));
        return capability;
    }

    @Override
    public AiProviderTestResponse testConnection(AiProviderTestRequest request, String userId) {
        long start = System.currentTimeMillis();
        AiProviderTestResponse response = new AiProviderTestResponse();
        try {
            AIChatRequest aiChatRequest = new AIChatRequest();
            aiChatRequest.setChatModelId(request.getChatModelId());
            aiChatRequest.setPrompt(StringUtils.defaultIfBlank(request.getPrompt(), "请回复 OK"));
            aiChatRequest.setConversationId(StringUtils.defaultIfBlank(request.getConversationId(), IDGenerator.nextStr()));
            aiChatRequest.setOrganizationId(request.getOrganizationId());
            AiModelSourceDTO module = aiChatBaseService.getModule(aiChatRequest, userId);
            String content = aiChatBaseService.chat(AIChatOption.builder()
                    .conversationId(aiChatRequest.getConversationId())
                    .module(module)
                    .prompt(aiChatRequest.getPrompt())
                    .build()).content();
            response.setSuccess(true);
            response.setContent(StringUtils.left(content, 1000));
        } catch (Exception ex) {
            response.setSuccess(false);
            response.setMessage(sanitize(ex.getMessage()));
        } finally {
            response.setDurationMs(System.currentTimeMillis() - start);
            log.info("ai_provider_test result={}", JSON.toJSONString(response));
        }
        return response;
    }

    @Override
    public AiProviderInvocationResult invoke(AiProviderChatRequest request, String userId) {
        return invokeInternal(request, userId, true);
    }

    @Override
    public AiProviderInvocationResult invokeAdmitted(AiProviderChatRequest request, String userId) {
        return invokeInternal(request, userId, false);
    }

    private AiProviderInvocationResult invokeInternal(AiProviderChatRequest request, String userId,
                                                      boolean checkTaskAdmission) {
        aiGovernanceService.assertModelAllowed(request.getProjectId(), request.getChatModelId());
        if (checkTaskAdmission) {
            aiGovernanceService.assertCanStartGeneration(request.getProjectId());
        }
        assertRateLimit(userId, request.getChatModelId());
        long start = System.currentTimeMillis();
        Exception last = null;
        List<String> candidates = fallbackCandidates(request.getProjectId(), request.getChatModelId());
        providerLoop:
        for (String modelId : candidates) {
            try {
                aiGovernanceService.assertModelAllowed(request.getProjectId(), modelId);
            } catch (MSException denied) {
                continue;
            }
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    AiModelSourceDTO model = systemAIConfigService.getModelSourceDTOWithKey(modelId, userId);
                    ChatResponse chatResponse = aiChatBaseService.chat(AIChatOption.builder()
                            .conversationId(request.getConversationId())
                            .module(model)
                            .system(request.getSystem())
                            .prompt(request.getPrompt())
                            .build()).chatResponse();
                    AiProviderInvocationResult result = toResult(chatResponse, model, start, request.getPrompt(),
                            !StringUtils.equals(modelId, request.getChatModelId()));
                    aiGovernanceService.recordUsage(request.getProjectId(), userId, request.getConversationId(),
                            request.getRequestId(), modelId, model.getProviderName(), "CHAT",
                            result.getInputTokens(), result.getOutputTokens(), result.getTotalTokens(),
                            result.isTokenEstimated(), true, result.getDurationMs(), null);
                    audit(request, userId, modelId, true, result.getDurationMs(), result.isFallbackUsed(), null);
                    return result;
                } catch (Exception ex) {
                    last = ex;
                    if (!isTransient(ex)) {
                        break providerLoop;
                    }
                    if (attempt == MAX_ATTEMPTS) {
                        break;
                    }
                    backoff(attempt);
                }
            }
        }
        long duration = System.currentTimeMillis() - start;
        aiGovernanceService.recordUsage(request.getProjectId(), userId, request.getConversationId(),
                request.getRequestId(), request.getChatModelId(), null, "CHAT",
                0, 0, 0, false, false, duration, classify(last));
        audit(request, userId, request.getChatModelId(), false, duration, false, classify(last));
        throw new MSException("AI Provider 调用失败：" + sanitize(last == null ? null : last.getMessage()), last);
    }

    @Override
    public Flux<String> stream(AiProviderChatRequest request, String userId) {
        return streamInternal(request, userId, true, List.of(), ignored -> { });
    }

    @Override
    public Flux<String> streamAdmitted(AiProviderChatRequest request, String userId) {
        return streamInternal(request, userId, false, List.of(), ignored -> { });
    }

    @Override
    public Flux<String> streamAdmittedWithTools(AiProviderChatRequest request, String userId, List<Object> tools) {
        return streamInternal(request, userId, false, tools, ignored -> { });
    }

    @Override
    public Flux<String> streamAdmittedWithTools(AiProviderChatRequest request, String userId, List<Object> tools,
                                                java.util.function.Consumer<String> selectedModelListener) {
        return streamInternal(request, userId, false, tools, selectedModelListener);
    }

    private Flux<String> streamInternal(AiProviderChatRequest request, String userId, boolean checkTaskAdmission,
                                        List<Object> tools, java.util.function.Consumer<String> selectedModelListener) {
        aiGovernanceService.assertModelAllowed(request.getProjectId(), request.getChatModelId());
        if (checkTaskAdmission) {
            aiGovernanceService.assertCanStartGeneration(request.getProjectId());
        }
        assertRateLimit(userId, request.getChatModelId());
        long start = System.currentTimeMillis();
        AtomicInteger outputCharacters = new AtomicInteger();
        AtomicBoolean emitted = new AtomicBoolean();
        AtomicReference<AiModelSourceDTO> usedModel = new AtomicReference<>();
        Flux<ChatResponse> requestedStream = streamModel(request, userId, request.getChatModelId(), usedModel,
                tools, selectedModelListener)
                .retryWhen(Retry.backoff(MAX_ATTEMPTS - 1, Duration.ofMillis(200))
                        .maxBackoff(Duration.ofSeconds(1))
                        .filter(ex -> !emitted.get() && isTransient(ex))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
        Flux<ChatResponse> resilientStream = requestedStream.onErrorResume(ex -> {
            if (emitted.get() || !isTransient(ex)) {
                return Flux.error(ex);
            }
            String fallbackId = aiGovernanceService.get(request.getProjectId()).getFallbackModelId();
            if (StringUtils.isBlank(fallbackId) || StringUtils.equals(fallbackId, request.getChatModelId())) {
                return Flux.error(ex);
            }
            aiGovernanceService.assertModelAllowed(request.getProjectId(), fallbackId);
            return streamModel(request, userId, fallbackId, usedModel, tools, selectedModelListener)
                    .retryWhen(Retry.backoff(MAX_ATTEMPTS - 1, Duration.ofMillis(200))
                            .maxBackoff(Duration.ofSeconds(1))
                            .filter(retryError -> !emitted.get() && isTransient(retryError))
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
        });
        return resilientStream
                .map(response -> response.getResult() == null || response.getResult().getOutput() == null
                        ? "" : StringUtils.defaultString(response.getResult().getOutput().getText()))
                .filter(StringUtils::isNotEmpty)
                .doOnNext(chunk -> {
                    emitted.set(true);
                    outputCharacters.addAndGet(chunk.length());
                })
                .doOnComplete(() -> aiGovernanceService.recordUsage(request.getProjectId(), userId,
                        request.getConversationId(), request.getRequestId(), usedModel.get().getId(),
                        usedModel.get().getProviderName(), "STREAM", estimateTokens(request.getPrompt()),
                        estimateTokens(outputCharacters.get()), estimateTokens(request.getPrompt()) + estimateTokens(outputCharacters.get()),
                        true, true, System.currentTimeMillis() - start, null))
                .doOnComplete(() -> audit(request, userId, usedModel.get().getId(), true,
                        System.currentTimeMillis() - start,
                        !StringUtils.equals(usedModel.get().getId(), request.getChatModelId()), null))
                .doOnError(ex -> aiGovernanceService.recordUsage(request.getProjectId(), userId,
                        request.getConversationId(), request.getRequestId(), request.getChatModelId(),
                        null, "STREAM", 0, 0, 0, false, false,
                        System.currentTimeMillis() - start, classify(ex)))
                .doOnError(ex -> audit(request, userId, request.getChatModelId(), false,
                        System.currentTimeMillis() - start, false, classify(ex)))
                .doOnCancel(() -> {
                    long duration = System.currentTimeMillis() - start;
                    aiGovernanceService.recordUsage(request.getProjectId(), userId, request.getConversationId(),
                            request.getRequestId(), request.getChatModelId(), null, "STREAM",
                            estimateTokens(request.getPrompt()), estimateTokens(outputCharacters.get()),
                            estimateTokens(request.getPrompt()) + estimateTokens(outputCharacters.get()),
                            true, false, duration, "CLIENT_CANCELED");
                    audit(request, userId, request.getChatModelId(), false, duration, false, "CLIENT_CANCELED");
                });
    }

    private Flux<ChatResponse> streamModel(AiProviderChatRequest request, String userId, String modelId,
                                           AtomicReference<AiModelSourceDTO> usedModel, List<Object> tools,
                                           java.util.function.Consumer<String> selectedModelListener) {
        return Flux.defer(() -> {
            AiModelSourceDTO model = systemAIConfigService.getModelSourceDTOWithKey(modelId, userId);
            usedModel.set(model);
            selectedModelListener.accept(model.getId());
            AIChatOption option = AIChatOption.builder()
                    .conversationId(request.getConversationId())
                    .module(model)
                    .system(request.getSystem())
                    .prompt(request.getPrompt())
                    .build();
            return tools == null || tools.isEmpty()
                    ? aiChatBaseService.stream(option)
                    : aiChatBaseService.stream(option, tools);
        });
    }

    private AiProviderInvocationResult toResult(ChatResponse response, AiModelSourceDTO model, long start,
                                                String prompt, boolean fallback) {
        AiProviderInvocationResult result = new AiProviderInvocationResult();
        result.setContent(response == null || response.getResult() == null || response.getResult().getOutput() == null
                ? "" : response.getResult().getOutput().getText());
        result.setModelSourceId(model.getId());
        result.setProviderName(model.getProviderName());
        boolean providerUsageAvailable = response != null && response.getMetadata() != null
                && response.getMetadata().getUsage() != null
                && response.getMetadata().getUsage().getTotalTokens() > 0;
        if (providerUsageAvailable) {
            result.setInputTokens(response.getMetadata().getUsage().getPromptTokens());
            result.setOutputTokens(response.getMetadata().getUsage().getCompletionTokens());
            result.setTotalTokens(response.getMetadata().getUsage().getTotalTokens());
        }
        if (result.getInputTokens() <= 0) {
            result.setInputTokens(estimateTokens(prompt));
        }
        if (result.getOutputTokens() <= 0) {
            result.setOutputTokens(estimateTokens(result.getContent()));
        }
        if (result.getTotalTokens() <= 0) {
            result.setTotalTokens(result.getInputTokens() + result.getOutputTokens());
        }
        result.setTokenEstimated(!providerUsageAvailable);
        result.setDurationMs(System.currentTimeMillis() - start);
        result.setFallbackUsed(fallback);
        return result;
    }

    private List<String> fallbackCandidates(String projectId, String requested) {
        List<String> result = new java.util.ArrayList<>();
        result.add(requested);
        String configuredFallback = aiGovernanceService.get(projectId).getFallbackModelId();
        if (StringUtils.isNotBlank(configuredFallback) && !StringUtils.equals(configuredFallback, requested)) {
            result.add(configuredFallback);
        }
        return result;
    }

    private void assertRateLimit(String userId, String modelId) {
        long minute = System.currentTimeMillis() / 60_000L;
        String redisKey = "ms:ai:provider:rate:" + userId + ":" + modelId + ":" + minute;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(redisKey, 2, TimeUnit.MINUTES);
            }
            if (count != null && count > RATE_LIMIT_PER_MINUTE) {
                throw new MSException("AI Provider 请求过于频繁，请稍后重试");
            }
            return;
        } catch (MSException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("AI provider Redis rate limit unavailable; using local fallback: {}", sanitize(ex.getMessage()));
        }
        String key = userId + ":" + modelId;
        RateWindow window = rateWindows.compute(key, (ignored, current) -> {
            if (current == null || current.minute != minute) {
                return new RateWindow(minute, 1);
            }
            current.count++;
            return current;
        });
        if (window.count > RATE_LIMIT_PER_MINUTE) {
            throw new MSException("AI Provider 请求过于频繁，请稍后重试");
        }
    }

    private void audit(AiProviderChatRequest request, String userId, String modelId, boolean success,
                       long durationMs, boolean fallback, String errorCode) {
        aiAuditService.record(request.getProjectId(), request.getOrganizationId(), userId, modelId,
                "EXECUTE", success ? "AI_PROVIDER_INVOKE" : "AI_PROVIDER_INVOKE_FAILED",
                "/ai/provider/invoke", "POST", Map.of(
                        "requestId", StringUtils.defaultString(request.getRequestId()),
                        "conversationId", StringUtils.defaultString(request.getConversationId()),
                        "success", success,
                        "durationMs", durationMs,
                        "fallback", fallback,
                        "errorCode", StringUtils.defaultString(errorCode)));
    }

    private boolean isTransient(Throwable ex) {
        String message = StringUtils.lowerCase(ex == null ? null : ex.getMessage());
        return StringUtils.containsAny(message, "timeout", "timed out", "429", "too many", "502", "503", "504", "connection reset");
    }

    private String classify(Throwable ex) {
        String message = StringUtils.lowerCase(ex == null ? null : ex.getMessage());
        if (StringUtils.containsAny(message, "401", "403", "unauthorized", "forbidden")) return "AUTHENTICATION_FAILED";
        if (StringUtils.containsAny(message, "429", "too many")) return "RATE_LIMITED";
        if (StringUtils.containsAny(message, "timeout", "timed out")) return "TIMEOUT";
        return "PROVIDER_ERROR";
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(Math.min(1000L, 200L * attempt));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MSException("AI Provider 重试被中断", ex);
        }
    }

    private long estimateTokens(Object value) {
        int length = value instanceof Number number ? number.intValue() : StringUtils.length((String) value);
        return Math.max(1, (length + 3L) / 4L);
    }

    private static class RateWindow {
        private final long minute;
        private int count;
        private RateWindow(long minute, int count) { this.minute = minute; this.count = count; }
    }

    private String sanitize(String message) {
        if (StringUtils.isBlank(message)) {
            return "连接测试失败";
        }
        return message.replaceAll("(?i)(api[-_ ]?key|token|secret|authorization)\\s*[:=]\\s*[^\\s,;]+", "$1=******");
    }
}
