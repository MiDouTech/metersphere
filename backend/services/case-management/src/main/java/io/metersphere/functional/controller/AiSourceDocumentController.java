package io.metersphere.functional.controller;

import io.metersphere.functional.dto.AiSourceDocumentDTO;
import io.metersphere.functional.request.AiSourceDocumentIdRequest;
import io.metersphere.functional.request.AiSourceDocumentPageRequest;
import io.metersphere.functional.request.AiSourceDocumentUploadRequest;
import io.metersphere.functional.response.AiSourceDocumentPageResponse;
import io.metersphere.functional.service.AiSourceDocumentService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.security.CheckOwner;
import io.metersphere.system.utils.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "用例管理-功能用例-AI来源文档")
@RestController
@RequestMapping("/functional/case/ai/document")
public class AiSourceDocumentController {
    @Resource
    private AiSourceDocumentService aiSourceDocumentService;

    @PostMapping("/upload")
    @Operation(summary = "上传 AI 用例生成来源文档")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_UPLOAD)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public AiSourceDocumentDTO upload(@Validated @RequestPart("request") AiSourceDocumentUploadRequest request,
                                      @RequestPart("file") MultipartFile file) {
        return aiSourceDocumentService.upload(request, file, SessionUtils.getUserId());
    }

    @PostMapping("/page")
    @Operation(summary = "分页查询 AI 来源文档")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_READ)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public AiSourceDocumentPageResponse page(@Validated @RequestBody AiSourceDocumentPageRequest request) {
        return aiSourceDocumentService.page(request, SessionUtils.getUserId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询 AI 来源文档详情")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_READ)
    public AiSourceDocumentDTO get(@PathVariable String id, @RequestParam String projectId) {
        return aiSourceDocumentService.get(id, projectId, SessionUtils.getUserId());
    }

    @PostMapping("/retry")
    @Operation(summary = "重新解析 AI 来源文档")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_UPLOAD)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public void retry(@Validated @RequestBody AiSourceDocumentIdRequest request) {
        aiSourceDocumentService.retry(request, SessionUtils.getUserId());
    }

    @PostMapping("/delete")
    @Operation(summary = "删除 AI 来源文档")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_UPLOAD)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public void delete(@Validated @RequestBody AiSourceDocumentIdRequest request) {
        aiSourceDocumentService.delete(request, SessionUtils.getUserId());
    }

    @PostMapping("/download")
    @Operation(summary = "下载 AI 来源文档")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_READ)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public ResponseEntity<byte[]> download(@Validated @RequestBody AiSourceDocumentIdRequest request) {
        return aiSourceDocumentService.download(request, SessionUtils.getUserId());
    }
}
