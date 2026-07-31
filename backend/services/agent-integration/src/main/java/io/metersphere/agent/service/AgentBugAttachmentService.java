package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentAttachmentPurpose;
import io.metersphere.agent.constants.AgentErrorCode;
import io.metersphere.agent.dto.AgentResourceAttachmentDTO;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenProjectAccess;
import io.metersphere.bug.domain.Bug;
import io.metersphere.bug.dto.request.BugDeleteFileRequest;
import io.metersphere.bug.dto.response.BugFileDTO;
import io.metersphere.bug.enums.BugAttachmentSourceType;
import io.metersphere.bug.mapper.BugMapper;
import io.metersphere.bug.service.BugAttachmentService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.AgentTempAttachment;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentBugAttachmentService {
    @Resource
    private AgentTempAttachmentService agentTempAttachmentService;
    @Resource
    private BugAttachmentService bugAttachmentService;
    @Resource
    private BugMapper bugMapper;
    @Resource
    private AgentProjectService agentProjectService;
    @Resource
    private AgentExecLogService agentExecLogService;

    public List<AgentResourceAttachmentDTO> list(String projectId, String bugId) {
        Bug bug = requireBugInProject(projectId, bugId);
        List<BugFileDTO> files = bugAttachmentService.getAllBugFiles(bug.getId());
        List<AgentResourceAttachmentDTO> result = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(files)) {
            for (BugFileDTO file : files) {
                AgentResourceAttachmentDTO dto = new AgentResourceAttachmentDTO();
                dto.setId(file.getRefId());
                dto.setAssociationId(file.getRefId());
                dto.setFileId(file.getFileId());
                dto.setFileName(file.getFileName());
                dto.setSize(file.getFileSize());
                dto.setCreateUser(file.getCreateUser());
                dto.setCreateUserName(file.getCreateUserName());
                dto.setCreateTime(file.getCreateTime());
                dto.setLocal(file.getLocal());
                dto.setSource(BugAttachmentSourceType.ATTACHMENT.name());
                result.add(dto);
            }
        }
        return result;
    }

    public Map<String, Object> attach(String projectId, String bugId, List<String> attachmentIds) {
        Bug bug = requireBugInProject(projectId, bugId);
        List<AgentTempAttachment> temps = agentTempAttachmentService.requireUsable(
                attachmentIds, bug.getProjectId(), AgentAttachmentPurpose.BUG_DETAIL);
        List<String> fileIds = agentTempAttachmentService.toFileIds(temps);
        bugAttachmentService.transferTmpFile(
                bug.getId(),
                bug.getProjectId(),
                fileIds,
                SessionUtils.getUserId(),
                BugAttachmentSourceType.ATTACHMENT.name());
        agentTempAttachmentService.markLinked(temps);
        Map<String, Object> audit = new HashMap<>();
        audit.put("bugId", bugId);
        audit.put("attachmentIds", attachmentIds);
        audit.put("fileIds", fileIds);
        agentExecLogService.audit("BUG_ATTACHMENT_ATTACH", bugId, JSON.toJSONString(audit));
        return Map.of("ok", true, "attached", fileIds.size());
    }

    public Map<String, Object> delete(String projectId, String bugId, String attachmentId, Boolean confirm) {
        if (!BooleanUtils.isTrue(confirm)) {
            throw new MSException(AgentErrorCode.CONFIRMATION_REQUIRED, "删除附件需要 confirm=true");
        }
        Bug bug = requireBugInProject(projectId, bugId);
        List<BugFileDTO> files = bugAttachmentService.getAllBugFiles(bug.getId());
        BugFileDTO target = files.stream()
                .filter(f -> StringUtils.equals(f.getRefId(), attachmentId) || StringUtils.equals(f.getFileId(), attachmentId))
                .findFirst()
                .orElseThrow(() -> new MSException(AgentErrorCode.RESOURCE_NOT_FOUND, "附件不属于目标缺陷"));

        BugDeleteFileRequest request = new BugDeleteFileRequest();
        request.setProjectId(bug.getProjectId());
        request.setBugId(bug.getId());
        request.setRefId(target.getRefId());
        request.setAssociated(!Boolean.TRUE.equals(target.getLocal()));
        bugAttachmentService.deleteFile(request);

        Map<String, Object> audit = new HashMap<>();
        audit.put("bugId", bugId);
        audit.put("attachmentId", attachmentId);
        agentExecLogService.audit("BUG_ATTACHMENT_DELETE", bugId, JSON.toJSONString(audit));
        return Map.of("ok", true);
    }

    private Bug requireBugInProject(String projectId, String bugId) {
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        assertProjectAllowed(resolvedProjectId);
        Bug bug = bugMapper.selectByPrimaryKey(bugId);
        if (bug == null || Boolean.TRUE.equals(bug.getDeleted())) {
            throw new MSException(AgentErrorCode.RESOURCE_NOT_FOUND, "缺陷不存在: " + bugId);
        }
        if (!StringUtils.equals(bug.getProjectId(), resolvedProjectId)) {
            throw new MSException(AgentErrorCode.RESOURCE_PROJECT_MISMATCH, "缺陷不属于指定项目");
        }
        return bug;
    }

    private void assertProjectAllowed(String projectId) {
        AgentToken token = AgentTokenContext.get();
        if (token != null && !AgentTokenProjectAccess.allows(token, projectId)) {
            throw new MSException(AgentErrorCode.PROJECT_NOT_ALLOWED, "Token 无权访问项目: " + projectId);
        }
    }
}
