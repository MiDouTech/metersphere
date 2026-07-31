package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentAttachmentPurpose;
import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.agent.constants.AgentErrorCode;
import io.metersphere.agent.dto.AgentTempAttachmentUploadResponse;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenProjectAccess;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentExecAttachment;
import io.metersphere.system.domain.AgentTempAttachment;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.mapper.AgentExecAttachmentMapper;
import io.metersphere.system.mapper.AgentTempAttachmentMapper;
import io.metersphere.system.service.CommonFileService;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentTempAttachmentService {
    private static final List<String> BLOCKED_EXTENSIONS = List.of("html", "htm", "svg", "js", "mjs", "exe", "bat", "cmd", "sh");

    @Resource
    private CommonFileService commonFileService;
    @Resource
    private AgentTempAttachmentMapper agentTempAttachmentMapper;
    @Resource
    private AgentExecAttachmentMapper agentExecAttachmentMapper;
    @Resource
    private AgentProjectService agentProjectService;

    public AgentTempAttachmentUploadResponse upload(MultipartFile file, String projectId, String purpose, Integer stepNum) {
        if (file == null || file.isEmpty()) {
            throw new MSException("上传文件不能为空");
        }
        if (file.getSize() > AgentConstants.MAX_ATTACHMENT_SIZE_BYTES) {
            throw new MSException(AgentErrorCode.ATTACHMENT_LIMIT_EXCEEDED, "单文件大小不能超过 5MB");
        }
        AgentAttachmentPurpose attachmentPurpose = AgentAttachmentPurpose.from(purpose);
        if (attachmentPurpose == null) {
            throw new MSException(AgentErrorCode.ATTACHMENT_PURPOSE_MISMATCH,
                    "purpose 必须为 CASE_DETAIL/CASE_COMMENT/BUG_DETAIL/BUG_COMMENT/EXECUTION");
        }
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        assertProjectAllowed(resolvedProjectId);

        String originalName = sanitizeFileName(file.getOriginalFilename());
        assertExtensionAllowed(originalName);

        String fileId = commonFileService.uploadTempImgFile(file);
        long now = System.currentTimeMillis();
        String userId = requireUserId();
        AgentToken token = AgentTokenContext.get();

        AgentTempAttachment temp = new AgentTempAttachment();
        temp.setId(IDGenerator.nextStr());
        temp.setTokenId(token == null ? "anonymous" : token.getId());
        temp.setUserId(userId);
        temp.setProjectId(resolvedProjectId);
        temp.setFileId(fileId);
        temp.setFileName(originalName);
        temp.setContentType(StringUtils.defaultIfBlank(file.getContentType(), "application/octet-stream"));
        temp.setSize(file.getSize());
        temp.setPurpose(attachmentPurpose.name());
        temp.setStepNum(stepNum);
        temp.setLinked(false);
        temp.setExpiresAt(now + AgentConstants.TEMP_ATTACHMENT_TTL_MS);
        temp.setCreateTime(now);
        agentTempAttachmentMapper.insert(temp);

        // EXECUTION 同步写入旧表，兼容现有 functional.submit attachmentIds
        if (attachmentPurpose.isExecution()) {
            AgentExecAttachment exec = new AgentExecAttachment();
            exec.setId(temp.getId());
            exec.setFileId(fileId);
            exec.setFileName(originalName);
            exec.setStepNum(stepNum);
            exec.setCreateTime(now);
            exec.setCreateUser(userId);
            agentExecAttachmentMapper.insert(exec);
        }

        AgentTempAttachmentUploadResponse response = new AgentTempAttachmentUploadResponse();
        response.setAttachmentId(temp.getId());
        response.setFileId(fileId);
        response.setFileName(originalName);
        response.setContentType(temp.getContentType());
        response.setSize(temp.getSize());
        response.setPurpose(temp.getPurpose());
        response.setExpiresAt(temp.getExpiresAt());
        response.setDownloadPath(AgentConstants.API_PREFIX + "/attachment/download/" + resolvedProjectId + "/" + fileId);
        return response;
    }

    public List<AgentTempAttachment> requireUsable(List<String> attachmentIds, String projectId, AgentAttachmentPurpose expectedPurpose) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        if (attachmentIds.size() > AgentConstants.MAX_ATTACHMENTS_PER_SUBMIT) {
            throw new MSException(AgentErrorCode.ATTACHMENT_LIMIT_EXCEEDED,
                    "单次业务操作最多关联 " + AgentConstants.MAX_ATTACHMENTS_PER_SUBMIT + " 个附件");
        }
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        assertProjectAllowed(resolvedProjectId);
        long now = System.currentTimeMillis();
        List<AgentTempAttachment> result = new ArrayList<>();
        for (String attachmentId : attachmentIds) {
            AgentTempAttachment attachment = agentTempAttachmentMapper.selectByPrimaryKey(attachmentId);
            if (attachment == null) {
                throw new MSException(AgentErrorCode.RESOURCE_NOT_FOUND, "临时附件不存在: " + attachmentId);
            }
            if (!StringUtils.equals(attachment.getProjectId(), resolvedProjectId)) {
                throw new MSException(AgentErrorCode.RESOURCE_PROJECT_MISMATCH, "临时附件不属于指定项目");
            }
            if (Boolean.TRUE.equals(attachment.getLinked())) {
                throw new MSException(AgentErrorCode.RESOURCE_NOT_FOUND, "临时附件已关联，不可重复使用: " + attachmentId);
            }
            if (attachment.getExpiresAt() != null && attachment.getExpiresAt() < now) {
                throw new MSException(AgentErrorCode.ATTACHMENT_EXPIRED, "临时附件已过期: " + attachmentId);
            }
            AgentAttachmentPurpose actual = AgentAttachmentPurpose.from(attachment.getPurpose());
            if (expectedPurpose != null && actual != expectedPurpose) {
                throw new MSException(AgentErrorCode.ATTACHMENT_PURPOSE_MISMATCH,
                        "附件用途不匹配，期望 " + expectedPurpose.name() + "，实际 " + attachment.getPurpose());
            }
            result.add(attachment);
        }
        return result;
    }

    public void markLinked(List<AgentTempAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        for (AgentTempAttachment attachment : attachments) {
            AgentTempAttachment update = new AgentTempAttachment();
            update.setId(attachment.getId());
            update.setLinked(true);
            agentTempAttachmentMapper.updateByPrimaryKeySelective(update);
        }
    }

    public List<String> toFileIds(List<AgentTempAttachment> attachments) {
        return attachments.stream().map(AgentTempAttachment::getFileId).toList();
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    public void cleanupExpired() {
        List<AgentTempAttachment> expired = agentTempAttachmentMapper.selectExpiredUnlinked(System.currentTimeMillis(), 200);
        for (AgentTempAttachment attachment : expired) {
            agentTempAttachmentMapper.deleteByPrimaryKey(attachment.getId());
        }
    }

    private void assertProjectAllowed(String projectId) {
        AgentToken token = AgentTokenContext.get();
        if (token != null && !AgentTokenProjectAccess.allows(token, projectId)) {
            throw new MSException(AgentErrorCode.PROJECT_NOT_ALLOWED, "Token 无权访问项目: " + projectId);
        }
    }

    private String requireUserId() {
        String userId = SessionUtils.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new MSException("Cannot resolve user bound to Agent Token");
        }
        return userId;
    }

    private String sanitizeFileName(String originalFilename) {
        String name = StringUtils.trimToEmpty(originalFilename);
        if (StringUtils.isBlank(name)) {
            throw new MSException(AgentErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED, "文件名不能为空");
        }
        name = name.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replace("..", "_");
        if (StringUtils.isBlank(name)) {
            throw new MSException(AgentErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED, "非法文件名");
        }
        return name;
    }

    private void assertExtensionAllowed(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return;
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (BLOCKED_EXTENSIONS.contains(ext)) {
            throw new MSException(AgentErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED, "不允许的文件类型: " + ext);
        }
    }
}
