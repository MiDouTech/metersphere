package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentAttachmentPurpose;
import io.metersphere.agent.constants.AgentErrorCode;
import io.metersphere.agent.dto.AgentResourceAttachmentDTO;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenProjectAccess;
import io.metersphere.functional.constants.CaseFileSourceType;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseAttachment;
import io.metersphere.functional.domain.FunctionalCaseAttachmentExample;
import io.metersphere.functional.dto.FunctionalCaseAttachmentDTO;
import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.metersphere.functional.mapper.FunctionalCaseAttachmentMapper;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.functional.request.FunctionalCaseDeleteFileRequest;
import io.metersphere.functional.service.FunctionalCaseAttachmentService;
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
public class AgentFunctionalCaseAttachmentService {
    @Resource
    private AgentTempAttachmentService agentTempAttachmentService;
    @Resource
    private FunctionalCaseAttachmentService functionalCaseAttachmentService;
    @Resource
    private FunctionalCaseAttachmentMapper functionalCaseAttachmentMapper;
    @Resource
    private FunctionalCaseMapper functionalCaseMapper;
    @Resource
    private AgentProjectService agentProjectService;
    @Resource
    private AgentExecLogService agentExecLogService;

    public List<AgentResourceAttachmentDTO> list(String projectId, String caseId) {
        FunctionalCase functionalCase = requireCaseInProject(projectId, caseId);
        FunctionalCaseDetailDTO detail = new FunctionalCaseDetailDTO();
        detail.setId(functionalCase.getId());
        functionalCaseAttachmentService.getAttachmentInfo(detail);
        List<AgentResourceAttachmentDTO> result = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(detail.getAttachments())) {
            for (FunctionalCaseAttachmentDTO item : detail.getAttachments()) {
                AgentResourceAttachmentDTO dto = new AgentResourceAttachmentDTO();
                dto.setId(item.getAssociationId());
                dto.setAssociationId(item.getAssociationId());
                dto.setFileId(item.getId());
                dto.setFileName(item.getFileName());
                dto.setSize(item.getSize());
                dto.setCreateUser(item.getCreateUser());
                dto.setCreateUserName(item.getCreateUserName());
                dto.setCreateTime(item.getCreateTime());
                dto.setLocal(item.getLocal());
                dto.setSource(CaseFileSourceType.ATTACHMENT.name());
                result.add(dto);
            }
        }
        return result;
    }

    public Map<String, Object> attach(String projectId, String caseId, List<String> attachmentIds) {
        FunctionalCase functionalCase = requireCaseInProject(projectId, caseId);
        List<AgentTempAttachment> temps = agentTempAttachmentService.requireUsable(
                attachmentIds, functionalCase.getProjectId(), AgentAttachmentPurpose.CASE_DETAIL);
        List<String> fileIds = agentTempAttachmentService.toFileIds(temps);
        // 详情附件区读取 ATTACHMENT；CASE_DETAIL 仅用于富文本内嵌图
        functionalCaseAttachmentService.uploadMinioFile(
                functionalCase.getId(),
                functionalCase.getProjectId(),
                fileIds,
                SessionUtils.getUserId(),
                CaseFileSourceType.ATTACHMENT.toString());
        agentTempAttachmentService.markLinked(temps);
        Map<String, Object> audit = new HashMap<>();
        audit.put("caseId", caseId);
        audit.put("attachmentIds", attachmentIds);
        audit.put("fileIds", fileIds);
        agentExecLogService.audit("CASE_ATTACHMENT_ATTACH", caseId, JSON.toJSONString(audit));
        return Map.of("ok", true, "attached", fileIds.size());
    }

    public Map<String, Object> delete(String projectId, String caseId, String attachmentId, Boolean confirm) {
        if (!BooleanUtils.isTrue(confirm)) {
            throw new MSException(AgentErrorCode.CONFIRMATION_REQUIRED, "删除附件需要 confirm=true");
        }
        FunctionalCase functionalCase = requireCaseInProject(projectId, caseId);
        FunctionalCaseAttachment local = findLocalAttachment(functionalCase.getId(), attachmentId);
        FunctionalCaseDeleteFileRequest request = new FunctionalCaseDeleteFileRequest();
        request.setProjectId(functionalCase.getProjectId());
        request.setCaseId(functionalCase.getId());
        if (local != null) {
            request.setId(local.getFileId());
            request.setLocal(true);
        } else {
            // associationId 或 fileId
            request.setId(attachmentId);
            request.setLocal(false);
        }
        functionalCaseAttachmentService.deleteFile(request, SessionUtils.getUserId());
        Map<String, Object> audit = new HashMap<>();
        audit.put("caseId", caseId);
        audit.put("attachmentId", attachmentId);
        agentExecLogService.audit("CASE_ATTACHMENT_DELETE", caseId, JSON.toJSONString(audit));
        return Map.of("ok", true);
    }

    private FunctionalCaseAttachment findLocalAttachment(String caseId, String attachmentId) {
        FunctionalCaseAttachmentExample byAssoc = new FunctionalCaseAttachmentExample();
        byAssoc.createCriteria().andCaseIdEqualTo(caseId).andIdEqualTo(attachmentId);
        List<FunctionalCaseAttachment> byId = functionalCaseAttachmentMapper.selectByExample(byAssoc);
        if (CollectionUtils.isNotEmpty(byId)) {
            return byId.get(0);
        }
        FunctionalCaseAttachmentExample byFile = new FunctionalCaseAttachmentExample();
        byFile.createCriteria().andCaseIdEqualTo(caseId).andFileIdEqualTo(attachmentId);
        List<FunctionalCaseAttachment> byFileId = functionalCaseAttachmentMapper.selectByExample(byFile);
        if (CollectionUtils.isNotEmpty(byFileId)) {
            return byFileId.get(0);
        }
        return null;
    }

    private FunctionalCase requireCaseInProject(String projectId, String caseId) {
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        assertProjectAllowed(resolvedProjectId);
        FunctionalCase functionalCase = functionalCaseMapper.selectByPrimaryKey(caseId);
        if (functionalCase == null || Boolean.TRUE.equals(functionalCase.getDeleted())) {
            throw new MSException(AgentErrorCode.RESOURCE_NOT_FOUND, "用例不存在: " + caseId);
        }
        if (!StringUtils.equals(functionalCase.getProjectId(), resolvedProjectId)) {
            throw new MSException(AgentErrorCode.RESOURCE_PROJECT_MISMATCH, "用例不属于指定项目");
        }
        return functionalCase;
    }

    private void assertProjectAllowed(String projectId) {
        AgentToken token = AgentTokenContext.get();
        if (token != null && !AgentTokenProjectAccess.allows(token, projectId)) {
            throw new MSException(AgentErrorCode.PROJECT_NOT_ALLOWED, "Token 无权访问项目: " + projectId);
        }
    }
}
