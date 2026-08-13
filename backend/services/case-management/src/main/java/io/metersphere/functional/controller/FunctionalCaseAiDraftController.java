package io.metersphere.functional.controller;

import io.metersphere.functional.dto.FunctionalCaseAiDraftDTO;
import io.metersphere.functional.request.FunctionalCaseAiDraftBatchDeleteRequest;
import io.metersphere.functional.request.FunctionalCaseAiDraftBatchSaveRequest;
import io.metersphere.functional.request.FunctionalCaseAiDraftPageRequest;
import io.metersphere.functional.request.FunctionalCaseAiDraftRegenerateRequest;
import io.metersphere.functional.request.FunctionalCaseAiDraftReviewRequest;
import io.metersphere.functional.dto.FunctionalCaseAiDraftDTO;
import io.metersphere.functional.request.FunctionalCaseAiDraftUpsertRequest;
import io.metersphere.functional.request.FunctionalCaseAiGenerateRequest;
import io.metersphere.functional.request.FunctionalCaseAiGenerationCancelRequest;
import io.metersphere.functional.response.FunctionalCaseAiBatchSaveResponse;
import io.metersphere.functional.response.FunctionalCaseAiDraftPageResponse;
import io.metersphere.functional.response.FunctionalCaseAiGenerateResponse;
import io.metersphere.functional.service.FunctionalCaseAiDraftService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.security.CheckOwner;
import io.metersphere.system.utils.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用例管理-功能用例-AI生成草稿")
@RestController
@RequestMapping("/functional/case/ai/draft")
public class FunctionalCaseAiDraftController {
    @Resource
    private FunctionalCaseAiDraftService functionalCaseAiDraftService;

    @PostMapping("/generation/structured")
    @Operation(summary = "结构化生成 AI 用例草稿")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public FunctionalCaseAiGenerateResponse generate(@Validated @RequestBody FunctionalCaseAiGenerateRequest request) {
        return functionalCaseAiDraftService.generate(request, SessionUtils.getUserId());
    }

    @PostMapping("/page")
    @Operation(summary = "分页查询 AI 用例草稿")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_READ)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public FunctionalCaseAiDraftPageResponse page(@Validated @RequestBody FunctionalCaseAiDraftPageRequest request) {
        return functionalCaseAiDraftService.page(request, SessionUtils.getUserId());
    }

    @PostMapping("/review-page")
    @Operation(summary = "Page project AI case drafts awaiting review")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_REVIEW)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public FunctionalCaseAiDraftPageResponse reviewPage(
            @Validated @RequestBody FunctionalCaseAiDraftPageRequest request) {
        return functionalCaseAiDraftService.reviewQueue(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询 AI 用例草稿详情")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_READ)
    public FunctionalCaseAiDraftDTO get(@PathVariable String id, @RequestParam String projectId) {
        return functionalCaseAiDraftService.get(id, projectId, SessionUtils.getUserId());
    }

    @PostMapping("/update")
    @Operation(summary = "更新 AI 用例草稿")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public FunctionalCaseAiDraftDTO update(@Validated @RequestBody FunctionalCaseAiDraftUpsertRequest request) {
        return functionalCaseAiDraftService.update(request, SessionUtils.getUserId());
    }

    @PostMapping("/delete")
    @Operation(summary = "删除 AI 用例草稿")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public void delete(@Validated @RequestBody FunctionalCaseAiDraftBatchDeleteRequest request) {
        functionalCaseAiDraftService.delete(request, SessionUtils.getUserId());
    }

    @PostMapping("/generation/cancel")
    @Operation(summary = "取消 AI 结构化生成任务")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public void cancel(@Validated @RequestBody FunctionalCaseAiGenerationCancelRequest request) {
        functionalCaseAiDraftService.cancel(request, SessionUtils.getUserId());
    }

    @PostMapping("/regenerate")
    @Operation(summary = "重新生成 AI 用例草稿")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public FunctionalCaseAiGenerateResponse regenerate(@Validated @RequestBody FunctionalCaseAiDraftRegenerateRequest request) {
        return functionalCaseAiDraftService.regenerate(request, SessionUtils.getUserId());
    }

    @PostMapping("/batch-save")
    @Operation(summary = "批量保存 AI 草稿为正式功能用例")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_SAVE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public FunctionalCaseAiBatchSaveResponse batchSave(@Validated @RequestBody FunctionalCaseAiDraftBatchSaveRequest request) {
        return functionalCaseAiDraftService.batchSave(
                request,
                SessionUtils.getUserId(),
                SessionUtils.getCurrentOrganizationId());
    }

    @PostMapping("/review")
    @Operation(summary = "审核 AI 用例草稿")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_REVIEW)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public java.util.List<FunctionalCaseAiDraftDTO> review(
            @Validated @RequestBody FunctionalCaseAiDraftReviewRequest request) {
        return functionalCaseAiDraftService.review(request, SessionUtils.getUserId());
    }
}
