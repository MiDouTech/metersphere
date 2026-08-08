package io.metersphere.functional.service;

import io.metersphere.functional.dto.AiCaseConversationDTO;
import io.metersphere.functional.dto.AiCaseAvailableModelDTO;
import io.metersphere.functional.dto.AiCaseExecutionDTO;
import io.metersphere.functional.dto.AiCaseExecutionEventDTO;
import io.metersphere.functional.repository.AiCaseAgentRepository;
import io.metersphere.functional.request.AiCaseAgentCancelRequest;
import io.metersphere.functional.request.AiCaseAgentChatRequest;
import io.metersphere.system.service.ai.AiGovernanceService;
import io.metersphere.system.service.ai.provider.AiProviderAdapter;
import io.metersphere.system.uid.IDGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCaseAgentOrchestratorTests {
    private AiCaseAgentOrchestrator orchestrator;
    private AiCaseAgentRepository repository;
    private AiProviderAdapter providerAdapter;

    @BeforeEach
    void setUp() {
        orchestrator = new AiCaseAgentOrchestrator();
        repository = mock(AiCaseAgentRepository.class);
        providerAdapter = mock(AiProviderAdapter.class);
        AiCaseConversationService conversationService = mock(AiCaseConversationService.class);
        AiCaseAvailableModelService modelService = mock(AiCaseAvailableModelService.class);
        AiGovernanceService governanceService = mock(AiGovernanceService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(governanceService).admitGeneration(anyString(), any(Runnable.class));
        when(conversationService.get("conversation-1", "project-1", "user-1"))
                .thenReturn(conversation());
        AiCaseAvailableModelDTO availableModel = new AiCaseAvailableModelDTO();
        availableModel.setId("model-1");
        availableModel.setSupportsTools(true);
        when(modelService.requireAllowed("project-1", "model-1", "user-1")).thenReturn(availableModel);
        when(repository.lockConversation("conversation-1", "project-1", "user-1")).thenReturn(true);
        when(repository.listMessages("conversation-1", "project-1", "user-1", null, null, 30))
                .thenReturn(List.of());
        AtomicLong sequence = new AtomicLong();
        when(repository.appendEvent(anyString(), anyString(), anyString(), anyLong())).thenAnswer(invocation -> {
            AiCaseExecutionEventDTO event = new AiCaseExecutionEventDTO();
            event.setRequestId(invocation.getArgument(0));
            event.setEventType(invocation.getArgument(1));
            event.setPayload(invocation.getArgument(2));
            event.setSequence(sequence.incrementAndGet());
            event.setCreateTime(invocation.getArgument(3));
            event.setTimestamp(event.getCreateTime());
            return event;
        });
        ReflectionTestUtils.setField(orchestrator, "repository", repository);
        ReflectionTestUtils.setField(orchestrator, "conversationService", conversationService);
        ReflectionTestUtils.setField(orchestrator, "availableModelService", modelService);
        ReflectionTestUtils.setField(orchestrator, "promptService", new AiCaseAgentPromptService());
        ReflectionTestUtils.setField(orchestrator, "providerAdapter", providerAdapter);
        ReflectionTestUtils.setField(orchestrator, "governanceService", governanceService);
        ReflectionTestUtils.setField(orchestrator, "transactionManager", transactionManager);
    }

    @Test
    void persistsOrderedEventsAndCompletesWithoutDependingOnClientSubscription() {
        when(providerAdapter.streamAdmittedWithTools(any(), eq("user-1"), anyList(), any()))
                .thenReturn(Flux.just("第一段", "第二段"));

        Flux<AiCaseExecutionEventDTO> stream;
        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("user-message", "assistant-message", "execution-id");
            stream = orchestrator.chat(chatRequest(), "user-1");
        }
        List<AiCaseExecutionEventDTO> events = stream.collectList().block();

        assertEquals(List.of("execution-start", "message-start", "content-delta", "content-delta",
                        "usage", "message-completed", "execution-completed"),
                events.stream().map(AiCaseExecutionEventDTO::getEventType).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L),
                events.stream().map(AiCaseExecutionEventDTO::getSequence).toList());
        verify(repository).completeMessage(eq("assistant-message"), eq("project-1"), eq("user-1"),
                eq("COMPLETED"), eq("第一段第二段"), eq("model-1"), anyLong(), anyLong(),
                eq(true), eq(null), anyLong());
        verify(repository).completeExecution(eq("request-1"), eq("project-1"), eq("user-1"),
                eq("COMPLETED"), anyLong(), anyLong(), eq(true), eq(null), eq(null), anyLong(), anyLong());
    }

    @Test
    void cancelDisposesProviderAndPersistsCanceledTerminalState() {
        when(providerAdapter.streamAdmittedWithTools(any(), eq("user-1"), anyList(), any())).thenReturn(Flux.never());
        AiCaseExecutionDTO running = new AiCaseExecutionDTO();
        running.setRequestId("request-1");
        running.setStatus("RUNNING");
        when(repository.findExecution("request-1", "project-1", "user-1"))
                .thenReturn(null, running);

        Flux<AiCaseExecutionEventDTO> stream;
        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("user-message", "assistant-message", "execution-id");
            stream = orchestrator.chat(chatRequest(), "user-1");
        }
        AiCaseAgentCancelRequest cancel = new AiCaseAgentCancelRequest();
        cancel.setProjectId("project-1");
        cancel.setRequestId("request-1");
        orchestrator.cancel(cancel, "user-1");
        List<AiCaseExecutionEventDTO> events = stream.collectList().block();

        assertTrue(events.stream().anyMatch(event -> "execution-completed".equals(event.getEventType())
                && event.getPayload().contains("CANCELED")));
        verify(repository).requestCancel(eq("request-1"), eq("project-1"), eq("user-1"), anyLong());
        verify(repository).completeExecution(eq("request-1"), eq("project-1"), eq("user-1"),
                eq("CANCELED"), anyLong(), anyLong(), anyBoolean(), eq("AI_AGENT_CANCELED"),
                anyString(), anyLong(), anyLong());
    }

    private AiCaseAgentChatRequest chatRequest() {
        AiCaseAgentChatRequest request = new AiCaseAgentChatRequest();
        request.setProjectId("project-1");
        request.setConversationId("conversation-1");
        request.setRequestId("request-1");
        request.setMessage("请生成登录测试用例");
        return request;
    }

    private AiCaseConversationDTO conversation() {
        AiCaseConversationDTO conversation = new AiCaseConversationDTO();
        conversation.setId("conversation-1");
        conversation.setProjectId("project-1");
        conversation.setOrganizationId("organization-1");
        conversation.setUserId("user-1");
        conversation.setModelSourceId("model-1");
        conversation.setStatus("ACTIVE");
        return conversation;
    }
}
