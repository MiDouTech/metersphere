package io.metersphere.functional.controller;

import io.metersphere.functional.domain.FunctionalCaseComment;
import io.metersphere.functional.dto.FunctionalCaseCommentDTO;
import io.metersphere.functional.request.FunctionalCaseCommentRequest;
import io.metersphere.functional.service.FunctionalCaseCommentService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.security.CheckOwner;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.validation.groups.Created;
import io.metersphere.validation.groups.Updated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用例管理-功能用例-用例评论")
@RestController
@RequestMapping("/functional/case/comment")
public class FunctionalCaseCommentController {

    @Resource
    private FunctionalCaseCommentService functionalCaseCommentService;
    @Resource private io.metersphere.functional.service.FunctionalCaseCommentLifecycleService lifecycle;
    @Resource private io.metersphere.functional.service.FunctionalCaseCommentOutbox outbox;

    public record RetryNotice(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) Long expectedUpdateTime) { }
    @GetMapping("/notices/{caseId}")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ)
    public java.util.Map<String,Object> notices(@PathVariable String caseId,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int pageSize) {
        return outbox.list(caseId,SessionUtils.getUserId(),current,pageSize);
    }
    @PostMapping("/notices/{caseId}/{id}/retry")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ_COMMENT)
    public void retryNotice(@PathVariable String caseId,@PathVariable String id,@Validated @RequestBody RetryNotice request) {
        outbox.retry(caseId,id,SessionUtils.getUserId(),request.expectedUpdateTime());
    }

    public record DeleteCommentRequest(@jakarta.validation.constraints.NotBlank String caseId,
                                       @jakarta.validation.constraints.NotBlank String commentId,
                                       @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) Long expectedUpdateTime) { }

    @PostMapping("/delete")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ_COMMENT)
    @CheckOwner(resourceId = "#request.caseId()", resourceType = "functional_case")
    public io.metersphere.functional.service.FunctionalCaseCommentLifecycleService.DeletedComment deleteVersioned(@Validated @RequestBody DeleteCommentRequest request) {
        return lifecycle.delete(request.caseId(), request.commentId(), SessionUtils.getUserId(), request.expectedUpdateTime());
    }

    @GetMapping("/page/{caseId}")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ)
    @CheckOwner(resourceId = "#caseId", resourceType = "functional_case")
    public io.metersphere.functional.service.FunctionalCaseCommentLifecycleService.CommentPage page(
            @PathVariable String caseId, @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize, @RequestParam(required = false) String parentId) {
        return lifecycle.page(caseId, SessionUtils.getUserId(), parentId, current, pageSize);
    }

    @PostMapping("/save")
    @Operation(summary = "用例管理-功能用例-用例评论-创建评论")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ_COMMENT)
    @CheckOwner(resourceId = "#functionalCaseCommentRequest.getCaseId()", resourceType = "functional_case")
    public FunctionalCaseComment saveComment(@Validated({Created.class}) @RequestBody FunctionalCaseCommentRequest functionalCaseCommentRequest) {
        return functionalCaseCommentService.saveComment(functionalCaseCommentRequest, SessionUtils.getUserId());
    }

    @PostMapping("/update")
    @Operation(summary = "用例管理-功能用例-用例评论-修改评论")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ_COMMENT)
    @CheckOwner(resourceId = "#functionalCaseCommentRequest.getCaseId()", resourceType = "functional_case")
    public FunctionalCaseComment updateComment(@Validated({Updated.class}) @RequestBody FunctionalCaseCommentRequest functionalCaseCommentRequest) {
        return functionalCaseCommentService.updateComment(functionalCaseCommentRequest, SessionUtils.getUserId());
    }

    @GetMapping("/delete/{commentId}")
    @Operation(summary = "用例管理-功能用例-用例评论-删除评论")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ_COMMENT)
    public void deleteComment(@PathVariable String commentId) {
        functionalCaseCommentService.deleteComment(commentId, SessionUtils.getUserId());
    }

    @GetMapping("/get/list/{caseId}")
    @Operation(summary = "用例管理-功能用例-用例评论-获取用例评论")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ)
    @CheckOwner(resourceId = "#caseId", resourceType = "functional_case")
    public List<FunctionalCaseCommentDTO> getCommentList(@PathVariable String caseId) {
       return functionalCaseCommentService.getCommentList(caseId);
    }
}
