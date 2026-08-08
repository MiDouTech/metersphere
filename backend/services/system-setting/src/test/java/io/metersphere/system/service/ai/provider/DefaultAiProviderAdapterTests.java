package io.metersphere.system.service.ai.provider;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.dto.request.ai.AiModelSourceDTO;
import io.metersphere.system.dto.request.ai.AiProjectGovernanceDTO;
import io.metersphere.system.dto.request.ai.AiProviderChatRequest;
import io.metersphere.system.dto.request.ai.AiProviderInvocationResult;
import io.metersphere.system.service.AiChatBaseService;
import io.metersphere.system.service.SystemAIConfigService;
import io.metersphere.system.service.ai.AiAuditService;
import io.metersphere.system.service.ai.AiGovernanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAiProviderAdapterTests {
    private DefaultAiProviderAdapter adapter;
    private SystemAIConfigService configService;
    private AiChatBaseService chatService;
    private AiGovernanceService governanceService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        adapter = new DefaultAiProviderAdapter();
        configService = mock(SystemAIConfigService.class);
        chatService = mock(AiChatBaseService.class);
        governanceService = mock(AiGovernanceService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);
        ReflectionTestUtils.setField(adapter, "systemAIConfigService", configService);
        ReflectionTestUtils.setField(adapter, "aiChatBaseService", chatService);
        ReflectionTestUtils.setField(adapter, "aiGovernanceService", governanceService);
        ReflectionTestUtils.setField(adapter, "aiAuditService", mock(AiAuditService.class));
        ReflectionTestUtils.setField(adapter, "stringRedisTemplate", redis);
    }

    @Test
    void retriesTransientFailureThenUsesConfiguredFallbackAndRecordsTokens() {
        AiProjectGovernanceDTO governance = new AiProjectGovernanceDTO();
        governance.setFallbackModelId("fallback");
        when(governanceService.get("project-1")).thenReturn(governance);
        when(configService.getModelSourceNameList("user-1")).thenReturn(List.of());
        when(configService.getModelSourceDTOWithKey("requested", "user-1"))
                .thenThrow(new IllegalStateException("503 upstream unavailable"));
        AiModelSourceDTO fallback = model("fallback");
        when(configService.getModelSourceDTOWithKey("fallback", "user-1")).thenReturn(fallback);

        ChatResponse response = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(response.getResult().getOutput().getText()).thenReturn("generated cases");
        when(response.getMetadata().getUsage().getPromptTokens()).thenReturn(12);
        when(response.getMetadata().getUsage().getCompletionTokens()).thenReturn(8);
        when(response.getMetadata().getUsage().getTotalTokens()).thenReturn(20);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(call.chatResponse()).thenReturn(response);
        when(chatService.chat(any())).thenReturn(call);

        AiProviderInvocationResult result = adapter.invoke(request(), "user-1");

        assertEquals("generated cases", result.getContent());
        assertEquals("fallback", result.getModelSourceId());
        assertEquals(20L, result.getTotalTokens());
        assertTrue(result.isFallbackUsed());
        verify(configService, times(3)).getModelSourceDTOWithKey("requested", "user-1");
        verify(governanceService).recordUsage("project-1", "user-1", "conversation-1", null,
                "fallback", "OPENAI", "CHAT", 12L, 8L, 20L, false,
                true, result.getDurationMs(), null);
    }

    @Test
    void masksCredentialsInProviderFailure() {
        AiProjectGovernanceDTO governance = new AiProjectGovernanceDTO();
        when(governanceService.get("project-1")).thenReturn(governance);
        when(configService.getModelSourceNameList("user-1")).thenReturn(List.of());
        when(configService.getModelSourceDTOWithKey("requested", "user-1"))
                .thenThrow(new IllegalStateException("api_key=super-secret-value"));

        MSException error = assertThrows(MSException.class, () -> adapter.invoke(request(), "user-1"));

        assertFalse(error.getMessage().contains("super-secret-value"));
        assertTrue(error.getMessage().contains("******"));
    }

    @Test
    void streamRetriesBeforeFirstChunkAndFallsBackWithoutDuplicatingOutput() {
        AiProjectGovernanceDTO governance = new AiProjectGovernanceDTO();
        governance.setFallbackModelId("fallback");
        when(governanceService.get("project-1")).thenReturn(governance);
        when(configService.getModelSourceDTOWithKey("requested", "user-1")).thenReturn(model("requested"));
        when(configService.getModelSourceDTOWithKey("fallback", "user-1")).thenReturn(model("fallback"));
        when(chatService.stream(argThat(option -> option != null && option.getModule() != null
                && "requested".equals(option.getModule().getId()))))
                .thenReturn(reactor.core.publisher.Flux.error(new IllegalStateException("503 upstream unavailable")));
        ChatResponse response = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(response.getResult().getOutput().getText()).thenReturn("fallback-stream");
        when(chatService.stream(argThat(option -> option != null && option.getModule() != null
                && "fallback".equals(option.getModule().getId()))))
                .thenReturn(reactor.core.publisher.Flux.just(response));

        List<String> chunks = adapter.stream(request(), "user-1").collectList().block();

        assertEquals(List.of("fallback-stream"), chunks);
        verify(configService, times(3)).getModelSourceDTOWithKey("requested", "user-1");
        verify(configService).getModelSourceDTOWithKey("fallback", "user-1");
    }

    @Test
    void streamDoesNotRetryAfterFirstChunkWasDelivered() {
        AiProjectGovernanceDTO governance = new AiProjectGovernanceDTO();
        governance.setFallbackModelId("fallback");
        when(governanceService.get("project-1")).thenReturn(governance);
        when(configService.getModelSourceDTOWithKey("requested", "user-1")).thenReturn(model("requested"));
        ChatResponse response = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(response.getResult().getOutput().getText()).thenReturn("partial");
        when(chatService.stream(any())).thenReturn(reactor.core.publisher.Flux.concat(
                reactor.core.publisher.Flux.just(response),
                reactor.core.publisher.Flux.error(new IllegalStateException("503 disconnected"))));

        assertThrows(IllegalStateException.class, () -> adapter.stream(request(), "user-1").collectList().block());

        verify(configService, times(1)).getModelSourceDTOWithKey("requested", "user-1");
        verify(configService, times(0)).getModelSourceDTOWithKey("fallback", "user-1");
    }

    private AiProviderChatRequest request() {
        AiProviderChatRequest request = new AiProviderChatRequest();
        request.setProjectId("project-1");
        request.setChatModelId("requested");
        request.setPrompt("generate");
        request.setConversationId("conversation-1");
        return request;
    }

    private AiModelSourceDTO model(String id) {
        AiModelSourceDTO model = new AiModelSourceDTO();
        model.setId(id);
        model.setProviderName("OPENAI");
        model.setBaseName("gpt-test");
        return model;
    }
}
