package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.agent.dto.AgentAttachmentDTO;
import io.metersphere.functional.constants.CaseFileSourceType;
import io.metersphere.functional.service.FunctionalCaseAttachmentService;
import io.metersphere.plan.dto.request.TestPlanCaseExecHistoryRequest;
import io.metersphere.plan.dto.response.TestPlanCaseExecHistoryResponse;
import io.metersphere.plan.service.TestPlanFunctionalCaseService;
import io.metersphere.sdk.constants.DefaultRepositoryDir;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.file.FileCenter;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.system.domain.AgentExecAttachment;
import io.metersphere.system.mapper.AgentExecAttachmentMapper;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentAttachmentService {
    @Resource
    private io.metersphere.system.service.CommonFileService commonFileService;
    @Resource
    private AgentExecAttachmentMapper agentExecAttachmentMapper;
    @Resource
    private FunctionalCaseAttachmentService functionalCaseAttachmentService;
    @Resource
    private TestPlanFunctionalCaseService testPlanFunctionalCaseService;

    public AgentAttachmentDTO get(String id) {
        AgentExecAttachment attachment = agentExecAttachmentMapper.selectByPrimaryKey(id);
        if (attachment == null) {
            return null;
        }
        return toDto(attachment, null);
    }

    public ResponseEntity<byte[]> download(String projectId, String fileId) {
        try {
            FileRequest fileRequest = new FileRequest();
            fileRequest.setFolder(DefaultRepositoryDir.getSystemTempDir() + "/" + fileId);
            String fileName = commonFileService.getTempFileNameByFileId(fileId);
            if (StringUtils.isBlank(fileName)) throw new MSException("附件不存在或已过期");
            fileRequest.setFileName(fileName);
            byte[] bytes = FileCenter.getDefaultRepository().getFile(fileRequest);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (Exception e) {
            throw new MSException("附件下载失败", e);
        }
    }

    public void linkToPlanSubmit(AgentCaseSubmitRequestHolder holder) {
        if (CollectionUtils.isEmpty(holder.attachmentIds())) {
            return;
        }
        validateAttachmentCount(holder.attachmentIds().size());
        String execHistoryId = resolveLatestExecHistoryId(holder.testPlanId(), holder.testPlanCaseId(), holder.caseId());
        List<String> fileIds = new ArrayList<>();
        for (String attachmentId : holder.attachmentIds()) {
            AgentExecAttachment attachment = agentExecAttachmentMapper.selectByPrimaryKey(attachmentId);
            if (attachment == null) {
                continue;
            }
            AgentExecAttachment update = new AgentExecAttachment();
            update.setId(attachmentId);
            update.setExecHistoryId(execHistoryId);
            agentExecAttachmentMapper.updateByPrimaryKeySelective(update);
            fileIds.add(attachment.getFileId());
        }
        if (CollectionUtils.isNotEmpty(fileIds)) {
            functionalCaseAttachmentService.uploadMinioFile(holder.caseId(), holder.projectId(), fileIds,
                    SessionUtils.getUserId(), CaseFileSourceType.PLAN_COMMENT.toString());
        }
    }

    public void linkToExecLog(String execLogId, List<String> attachmentIds) {
        if (CollectionUtils.isEmpty(attachmentIds)) {
            return;
        }
        validateAttachmentCount(attachmentIds.size());
        for (String attachmentId : attachmentIds) {
            AgentExecAttachment update = new AgentExecAttachment();
            update.setId(attachmentId);
            update.setExecLogId(execLogId);
            agentExecAttachmentMapper.updateByPrimaryKeySelective(update);
        }
    }

    public List<AgentAttachmentDTO> listByExecLogId(String execLogId) {
        return agentExecAttachmentMapper.selectByExecLogId(execLogId).stream()
                .map(item -> toDto(item, null))
                .collect(Collectors.toList());
    }

    private void validateAttachmentCount(int count) {
        if (count > AgentConstants.MAX_ATTACHMENTS_PER_SUBMIT) {
            throw new MSException("单次 submit 最多关联 " + AgentConstants.MAX_ATTACHMENTS_PER_SUBMIT + " 个附件");
        }
    }

    private String resolveLatestExecHistoryId(String testPlanId, String testPlanCaseId, String caseId) {
        TestPlanCaseExecHistoryRequest request = new TestPlanCaseExecHistoryRequest();
        request.setTestPlanId(testPlanId);
        request.setId(testPlanCaseId);
        request.setCaseId(caseId);
        List<TestPlanCaseExecHistoryResponse> histories = testPlanFunctionalCaseService.getCaseExecHistory(request);
        if (CollectionUtils.isEmpty(histories)) {
            return null;
        }
        return histories.get(0).getId();
    }

    private AgentAttachmentDTO toDto(AgentExecAttachment attachment, String projectId) {
        AgentAttachmentDTO dto = new AgentAttachmentDTO();
        dto.setId(attachment.getId());
        dto.setFileId(attachment.getFileId());
        dto.setFileName(attachment.getFileName());
        dto.setStepNum(attachment.getStepNum());
        if (StringUtils.isNotBlank(projectId) && StringUtils.isNotBlank(attachment.getFileId())) {
            dto.setDownloadPath(buildDownloadPath(projectId, attachment.getFileId()));
        }
        return dto;
    }

    private String buildDownloadPath(String projectId, String fileId) {
        return AgentConstants.API_PREFIX + "/functional/attachment/download/" + projectId + "/" + fileId;
    }

    public record AgentCaseSubmitRequestHolder(String projectId, String caseId, String testPlanId,
                                               String testPlanCaseId, List<String> attachmentIds) {
    }
}
