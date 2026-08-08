package io.metersphere.functional.service;

import io.metersphere.functional.constants.AiCaseConversationStatus;
import io.metersphere.functional.dto.AiCaseConversationDTO;
import io.metersphere.functional.dto.AiCaseConversationPageResponse;
import io.metersphere.functional.dto.AiCaseMessageDTO;
import io.metersphere.functional.dto.AiCaseMessagePageResponse;
import io.metersphere.functional.repository.AiCaseAgentRepository;
import io.metersphere.functional.request.AiCaseConversationCreateRequest;
import io.metersphere.functional.request.AiCaseConversationModelRequest;
import io.metersphere.functional.request.AiCaseConversationOperationRequest;
import io.metersphere.functional.request.AiCaseConversationPageRequest;
import io.metersphere.functional.request.AiCaseConversationRenameRequest;
import io.metersphere.functional.request.AiCaseMessagePageRequest;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.service.ai.AiAuditService;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class)
public class AiCaseConversationService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SYSTEM_PROMPT_VERSION = "case-agent-v1";

    @Resource
    private AiCaseAgentRepository repository;
    @Resource
    private AiCaseAvailableModelService availableModelService;
    @Resource
    private AiAuditService aiAuditService;
    @Resource
    private ProjectMapper projectMapper;

    public AiCaseConversationDTO create(AiCaseConversationCreateRequest request, String userId) {
        Project project = projectMapper.selectByPrimaryKey(request.getProjectId());
        if (project == null || Boolean.TRUE.equals(project.getDeleted())
                || !StringUtils.equals(project.getOrganizationId(), request.getOrganizationId())) {
            throw new MSException("项目不存在或组织与项目不匹配");
        }
        availableModelService.requireAllowed(request.getProjectId(), request.getModelSourceId(), userId);
        long now = System.currentTimeMillis();
        AiCaseConversationDTO conversation = new AiCaseConversationDTO();
        conversation.setId(IDGenerator.nextStr());
        conversation.setProjectId(request.getProjectId());
        conversation.setOrganizationId(request.getOrganizationId());
        conversation.setUserId(userId);
        conversation.setTitle(StringUtils.left(StringUtils.defaultIfBlank(StringUtils.trim(request.getTitle()), "新对话"), 255));
        conversation.setModelSourceId(request.getModelSourceId());
        conversation.setStatus(AiCaseConversationStatus.ACTIVE.name());
        conversation.setSystemPromptVersion(SYSTEM_PROMPT_VERSION);
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        repository.insertConversation(conversation);
        audit(conversation, userId, "AI_CASE_CONVERSATION_CREATE", Map.of(
                "modelSourceId", conversation.getModelSourceId()));
        return conversation;
    }

    @Transactional(readOnly = true)
    public AiCaseConversationPageResponse page(AiCaseConversationPageRequest request, String userId) {
        int current = Math.max(1, request.getCurrent() == null ? 1 : request.getCurrent());
        int pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1,
                request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize()));
        String status = normalizeStatus(request.getStatus());
        AiCaseConversationPageResponse response = new AiCaseConversationPageResponse();
        response.setTotal(repository.countConversations(request.getProjectId(), userId, status));
        response.setRecords(repository.pageConversations(request.getProjectId(), userId, status,
                (long) (current - 1) * pageSize, pageSize));
        return response;
    }

    @Transactional(readOnly = true)
    public AiCaseConversationDTO get(String id, String projectId, String userId) {
        return requireConversation(id, projectId, userId);
    }

    @Transactional(readOnly = true)
    public AiCaseMessagePageResponse messages(AiCaseMessagePageRequest request, String userId) {
        requireConversation(request.getConversationId(), request.getProjectId(), userId);
        int pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1,
                request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize()));
        List<AiCaseMessageDTO> descending = repository.listMessages(request.getConversationId(),
                request.getProjectId(), userId, request.getBeforeTime(), request.getBeforeId(), pageSize + 1);
        boolean hasMore = descending.size() > pageSize;
        if (hasMore) {
            descending = new ArrayList<>(descending.subList(0, pageSize));
        } else {
            descending = new ArrayList<>(descending);
        }
        AiCaseMessageDTO oldest = descending.isEmpty() ? null : descending.getLast();
        Collections.reverse(descending);
        AiCaseMessagePageResponse response = new AiCaseMessagePageResponse();
        response.setRecords(descending);
        response.setHasMore(hasMore);
        if (hasMore && oldest != null) {
            response.setNextBeforeTime(oldest.getCreateTime());
            response.setNextBeforeId(oldest.getId());
        }
        return response;
    }

    public AiCaseConversationDTO rename(AiCaseConversationRenameRequest request, String userId) {
        AiCaseConversationDTO conversation = requireConversation(request.getConversationId(), request.getProjectId(), userId);
        long now = System.currentTimeMillis();
        if (repository.updateConversationTitle(conversation.getId(), conversation.getProjectId(), userId,
                StringUtils.trim(request.getTitle()), now) == 0) {
            throw new MSException("会话不存在或无权限");
        }
        conversation.setTitle(StringUtils.trim(request.getTitle()));
        conversation.setUpdateTime(now);
        audit(conversation, userId, "AI_CASE_CONVERSATION_RENAME", Map.of());
        return conversation;
    }

    public AiCaseConversationDTO switchModel(AiCaseConversationModelRequest request, String userId) {
        AiCaseConversationDTO conversation = requireConversation(request.getConversationId(), request.getProjectId(), userId);
        availableModelService.requireAllowed(request.getProjectId(), request.getModelSourceId(), userId);
        if (repository.countActiveExecutions(conversation.getId(), conversation.getProjectId(), userId) > 0) {
            throw new MSException("当前会话存在运行中的 AI 请求，请结束后再切换模型");
        }
        long now = System.currentTimeMillis();
        if (repository.updateConversationModel(conversation.getId(), conversation.getProjectId(), userId,
                request.getModelSourceId(), now) == 0) {
            throw new MSException("会话不存在或无权限");
        }
        String previousModel = conversation.getModelSourceId();
        conversation.setModelSourceId(request.getModelSourceId());
        conversation.setUpdateTime(now);
        audit(conversation, userId, "AI_CASE_CONVERSATION_MODEL_SWITCH", Map.of(
                "previousModelSourceId", previousModel,
                "modelSourceId", request.getModelSourceId()));
        return conversation;
    }

    public AiCaseConversationDTO archive(AiCaseConversationOperationRequest request, String userId) {
        AiCaseConversationDTO conversation = requireConversation(request.getConversationId(), request.getProjectId(), userId);
        long now = System.currentTimeMillis();
        repository.updateConversationStatus(conversation.getId(), conversation.getProjectId(), userId,
                AiCaseConversationStatus.ARCHIVED.name(), now);
        conversation.setStatus(AiCaseConversationStatus.ARCHIVED.name());
        conversation.setUpdateTime(now);
        audit(conversation, userId, "AI_CASE_CONVERSATION_ARCHIVE", Map.of());
        return conversation;
    }

    public void delete(AiCaseConversationOperationRequest request, String userId) {
        AiCaseConversationDTO conversation = requireConversation(request.getConversationId(), request.getProjectId(), userId);
        if (repository.countActiveExecutions(conversation.getId(), conversation.getProjectId(), userId) > 0) {
            throw new MSException("当前会话存在运行中的 AI 请求，请先停止后再删除");
        }
        if (repository.softDeleteConversation(conversation.getId(), conversation.getProjectId(), userId,
                System.currentTimeMillis()) == 0) {
            throw new MSException("会话不存在或无权限");
        }
        audit(conversation, userId, "AI_CASE_CONVERSATION_DELETE", Map.of());
    }

    private AiCaseConversationDTO requireConversation(String id, String projectId, String userId) {
        AiCaseConversationDTO conversation = repository.findConversation(id, projectId, userId);
        if (conversation == null) {
            throw new MSException("会话不存在或无权限");
        }
        return conversation;
    }

    private String normalizeStatus(String status) {
        if (StringUtils.isBlank(status) || StringUtils.equalsIgnoreCase(status, "ALL")) {
            return null;
        }
        String normalized = StringUtils.upperCase(status);
        if (!List.of(AiCaseConversationStatus.ACTIVE.name(), AiCaseConversationStatus.ARCHIVED.name()).contains(normalized)) {
            throw new MSException("不支持的会话状态");
        }
        return normalized;
    }

    private void audit(AiCaseConversationDTO conversation, String userId, String action, Map<String, ?> detail) {
        aiAuditService.record(conversation.getProjectId(), conversation.getOrganizationId(), userId,
                conversation.getId(), "UPDATE", action, "/functional/case/ai/agent/conversation", "POST", detail);
    }
}
