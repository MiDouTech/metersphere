package io.metersphere.agent.controller;

import io.metersphere.agent.constants.AgentAttachmentPurpose;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentTempAttachmentUploadResponse;
import io.metersphere.agent.security.AgentScopeAssert;
import io.metersphere.agent.service.AgentAttachmentService;
import io.metersphere.agent.service.AgentTempAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Agent Attachment")
@RestController
@RequestMapping({"/agent/v1/attachment", "/api/agent/v1/attachment"})
public class AgentAttachmentController {
    @Resource
    private AgentTempAttachmentService agentTempAttachmentService;
    @Resource
    private AgentAttachmentService agentAttachmentService;

    @PostMapping("/upload")
    @Operation(summary = "上传 Agent 通用临时附件")
    public AgentTempAttachmentUploadResponse upload(@RequestParam("file") MultipartFile file,
                                                    @RequestParam String projectId,
                                                    @RequestParam String purpose,
                                                    @RequestParam(required = false) Integer stepNum) {
        AgentAttachmentPurpose attachmentPurpose = AgentAttachmentPurpose.from(purpose);
        if (attachmentPurpose != null && attachmentPurpose.isExecution()) {
            AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_SUBMIT);
        } else if (attachmentPurpose != null && (attachmentPurpose.isCaseDetail() || attachmentPurpose.isCaseComment())) {
            AgentScopeAssert.assertScope(AgentTokenScope.CASE_ATTACHMENT);
        } else if (attachmentPurpose != null && (attachmentPurpose.isBugDetail() || attachmentPurpose.isBugComment())) {
            AgentScopeAssert.assertScope(AgentTokenScope.BUG_ATTACHMENT);
        } else {
            // purpose 非法时仍要求任一附件写权限，具体错误由 service 抛出
            AgentScopeAssert.assertAnyScope(AgentTokenScope.CASE_ATTACHMENT, AgentTokenScope.BUG_ATTACHMENT, AgentTokenScope.FUNCTIONAL_SUBMIT);
        }
        return agentTempAttachmentService.upload(file, projectId, purpose, stepNum);
    }

    @GetMapping("/download/{projectId}/{fileId}")
    @Operation(summary = "下载临时附件")
    public ResponseEntity<byte[]> download(@PathVariable String projectId, @PathVariable String fileId) {
        AgentScopeAssert.assertAnyScope(AgentTokenScope.FUNCTIONAL_READ, AgentTokenScope.BUG_READ, AgentTokenScope.CASE_ATTACHMENT, AgentTokenScope.BUG_ATTACHMENT);
        return agentAttachmentService.download(projectId, fileId);
    }
}
