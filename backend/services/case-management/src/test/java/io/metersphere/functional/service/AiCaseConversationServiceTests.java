package io.metersphere.functional.service;

import io.metersphere.functional.dto.AiCaseConversationDTO;
import io.metersphere.functional.dto.AiCaseMessageDTO;
import io.metersphere.functional.dto.AiResourceSelection;
import io.metersphere.functional.repository.AiCaseAgentRepository;
import io.metersphere.functional.request.AiCaseConversationCreateRequest;
import io.metersphere.functional.request.AiCaseConversationModelRequest;
import io.metersphere.functional.request.AiCaseMessagePageRequest;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.service.ai.AiAuditService;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCaseConversationServiceTests {
    private AiCaseConversationService service;
    private AiCaseAgentRepository repository;
    private AiCaseAvailableResourceService availableResourceService;

    @BeforeEach
    void setUp() {
        service = new AiCaseConversationService();
        repository = mock(AiCaseAgentRepository.class);
        availableResourceService = mock(AiCaseAvailableResourceService.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        Project project = new Project();
        project.setId("project-1");
        project.setOrganizationId("organization-1");
        project.setDeleted(false);
        when(projectMapper.selectByPrimaryKey("project-1")).thenReturn(project);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "availableResourceService", availableResourceService);
        ReflectionTestUtils.setField(service, "aiAuditService", mock(AiAuditService.class));
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
    }

    @Test
    void createsServerOwnedConversationAfterModelAuthorization() {
        AiResourceSelection selection = new AiResourceSelection(
                "MODEL_API", "model-1", "model-1", null, null, true);
        when(availableResourceService.requireAllowed("project-1", null, null, "model-1", "user-1"))
                .thenReturn(selection);
        AiCaseConversationCreateRequest request = new AiCaseConversationCreateRequest();
        request.setProjectId("project-1");
        request.setOrganizationId("organization-1");
        request.setModelSourceId("model-1");
        request.setTitle("  ");

        AiCaseConversationDTO result;
        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("conversation-generated");
            result = service.create(request, "user-1");
        }

        assertEquals("conversation-generated", result.getId());
        assertEquals("user-1", result.getUserId());
        assertEquals("project-1", result.getProjectId());
        assertEquals("新对话", result.getTitle());
        assertEquals("ACTIVE", result.getStatus());
        verify(availableResourceService).requireAllowed("project-1", null, null, "model-1", "user-1");
        verify(repository).insertConversation(result);
    }

    @Test
    void blocksModelSwitchWhileRequestIsActive() {
        AiResourceSelection selection = new AiResourceSelection(
                "MODEL_API", "model-2", "model-2", null, null, true);
        when(availableResourceService.requireAllowed("project-1", null, null, "model-2", "user-1"))
                .thenReturn(selection);
        AiCaseConversationDTO conversation = conversation();
        when(repository.findConversation("conversation-1", "project-1", "user-1"))
                .thenReturn(conversation);
        when(repository.countActiveExecutions("conversation-1", "project-1", "user-1"))
                .thenReturn(1);
        AiCaseConversationModelRequest request = new AiCaseConversationModelRequest();
        request.setProjectId("project-1");
        request.setConversationId("conversation-1");
        request.setModelSourceId("model-2");

        assertThrows(MSException.class, () -> service.switchModel(request, "user-1"));

        verify(availableResourceService).requireAllowed("project-1", null, null, "model-2", "user-1");
        verify(repository, never()).updateConversationModel(any(), any(), any(), any(), anyLong());
    }

    @Test
    void returnsMessagesInDisplayOrderWithStableCursor() {
        when(repository.findConversation("conversation-1", "project-1", "user-1"))
                .thenReturn(conversation());
        AiCaseMessageDTO newest = message("m3", 30L);
        AiCaseMessageDTO middle = message("m2", 20L);
        AiCaseMessageDTO older = message("m1", 10L);
        when(repository.listMessages("conversation-1", "project-1", "user-1", null, null, 3))
                .thenReturn(List.of(newest, middle, older));
        AiCaseMessagePageRequest request = new AiCaseMessagePageRequest();
        request.setProjectId("project-1");
        request.setConversationId("conversation-1");
        request.setPageSize(2);

        var result = service.messages(request, "user-1");

        assertTrue(result.isHasMore());
        assertEquals(List.of("m2", "m3"), result.getRecords().stream().map(AiCaseMessageDTO::getId).toList());
        assertEquals(20L, result.getNextBeforeTime());
        assertEquals("m2", result.getNextBeforeId());
    }

    @Test
    void doesNotExposeConversationOwnedByAnotherUser() {
        when(repository.findConversation("conversation-1", "project-1", "user-2")).thenReturn(null);

        assertThrows(MSException.class, () -> service.get("conversation-1", "project-1", "user-2"));

        verify(repository).findConversation("conversation-1", "project-1", "user-2");
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

    private AiCaseMessageDTO message(String id, long createTime) {
        AiCaseMessageDTO message = new AiCaseMessageDTO();
        message.setId(id);
        message.setCreateTime(createTime);
        return message;
    }
}
