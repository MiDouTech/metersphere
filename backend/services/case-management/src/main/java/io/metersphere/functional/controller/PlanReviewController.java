package io.metersphere.functional.controller;

import io.metersphere.functional.planreview.PlanReviewApplicationService;
import io.metersphere.functional.planreview.PlanReviewFailure;
import io.metersphere.system.utils.SessionUtils;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Authentication at the edge, current object/project/action authorization in the shared service. */
@RestController
@RequestMapping("/plan-review")
@RequiresAuthentication
public class PlanReviewController {
    @jakarta.annotation.Resource private io.metersphere.functional.planreview.PlanReviewNoticeService notices;
    @jakarta.annotation.Resource private io.metersphere.functional.planreview.PlanReviewLegacyInventory legacy;
    @GetMapping("/legacy/scan")
    public Map<String,Object> legacyScan(@RequestParam String projectId){return legacy.scan(projectId,SessionUtils.getUserId());}
    @jakarta.annotation.Resource private io.metersphere.functional.planreview.PlanReviewLegacyMigrationService migrations;
    public record LegacyStep(String projectId,String changeId,String snapshotHash,boolean acknowledged,String stopEvidenceHash,String releaseSha,String previewHash) { }
    @PostMapping("/legacy/preview")
    public Map<String,Object> legacyPreview(@RequestBody io.metersphere.functional.planreview.PlanReviewLegacyDecision request){return migrations.preview(request,SessionUtils.getUserId());}
    @GetMapping("/legacy/change")
    public Map<String,Object> legacyChange(@RequestParam String projectId,@RequestParam String changeId){return migrations.details(projectId,changeId,SessionUtils.getUserId());}
    @GetMapping("/legacy/changes")
    public Map<String,Object> legacyChanges(@RequestParam String projectId){return migrations.recent(projectId,SessionUtils.getUserId());}
    @PostMapping("/legacy/backup")
    public Map<String,Object> legacyBackup(@RequestBody LegacyStep request){return migrations.backup(request.projectId(),request.changeId(),SessionUtils.getUserId());}
    @PostMapping("/legacy/rehearse")
    public Map<String,Object> legacyRehearse(@RequestBody LegacyStep request){return migrations.rehearse(request.projectId(),request.changeId(),SessionUtils.getUserId());}
    @PostMapping("/legacy/approve")
    public Map<String,Object> legacyApprove(@RequestBody LegacyStep request){return migrations.approve(request.projectId(),request.changeId(),request.snapshotHash(),request.stopEvidenceHash(),request.releaseSha(),request.acknowledged(),SessionUtils.getUserId());}
    @PostMapping("/legacy/execute")
    public Map<String,Object> legacyExecute(@RequestBody LegacyStep request){return migrations.execute(request.projectId(),request.changeId(),request.snapshotHash(),request.acknowledged(),SessionUtils.getUserId());}
    @PostMapping("/legacy/rollback-preview")
    public Map<String,Object> legacyRollbackPreview(@RequestBody LegacyStep request){return migrations.rollbackPreview(request.projectId(),request.changeId(),SessionUtils.getUserId());}
    @PostMapping("/legacy/rollback-approve")
    public Map<String,Object> legacyRollbackApprove(@RequestBody LegacyStep request){return migrations.approveRollback(request.projectId(),request.changeId(),request.previewHash(),request.acknowledged(),SessionUtils.getUserId());}
    @PostMapping("/legacy/rollback")
    public Map<String,Object> legacyRollback(@RequestBody LegacyStep request){return migrations.rollback(request.projectId(),request.changeId(),request.previewHash(),request.acknowledged(),SessionUtils.getUserId());}
    @jakarta.annotation.Resource private io.metersphere.functional.planreview.PlanReviewOperationService operations;
    @jakarta.annotation.Resource private io.metersphere.functional.planreview.PlanReviewLifecycleService lifecycle;
    private final PlanReviewApplicationService service;

    public PlanReviewController(PlanReviewApplicationService service) { this.service = service; }

    public record CandidateRequest(String side, String id, String keyword, int page, int size) { }
    @GetMapping("/difference/{side}/{id}")
    @org.springframework.transaction.annotation.Transactional
    public Map<String,Object> readOnlyDifference(@PathVariable String side, @PathVariable String id,
            @RequestParam String generation, @RequestParam String kind,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return service.readOnlyDifference(side,id,generation,kind,page,size,SessionUtils.getUserId());
    }
    public record PreviewRequest(String planId, String reviewId, String purpose) { }
    public record ConfirmRequest(String previewId, String requestKey, boolean acknowledged) { }
    public record UnlinkRequest(String relationId, String expectedGeneration, String requestKey) { }
    public record GrantRequest(String projectId, Long expectedRevision, Boolean enabled) { }
    public record OperationRequest(String projectId,long expectedRevision,String requestKey) { }
    public record NoticePreviewRequest(String projectId,String faultId) { }
    public record NoticeConfirmRequest(String projectId,String previewId,boolean acknowledged) { }
    @GetMapping("/notices")
    public Map<String,Object> notices(@RequestParam String projectId,@RequestParam String relationId){return notices.list(projectId,relationId,SessionUtils.getUserId());}
    @GetMapping("/notices/object")
    public Map<String,Object> objectNotices(@RequestParam String projectId,@RequestParam String side,@RequestParam String objectId){return notices.listObject(projectId,side,objectId,SessionUtils.getUserId());}
    @PostMapping("/notices/preview")
    public Map<String,Object> noticePreview(@RequestBody NoticePreviewRequest request){return notices.preview(request.projectId(),request.faultId(),SessionUtils.getUserId());}
    @PostMapping("/notices/confirm")
    public Map<String,Object> noticeConfirm(@RequestBody NoticeConfirmRequest request){return notices.confirm(request.projectId(),request.previewId(),request.acknowledged(),SessionUtils.getUserId());}
    public record TakeoverPreviewRequest(String projectId,long expectedRevision,String newActor) { }
    public record TakeoverApproveRequest(String projectId,String previewId,boolean acknowledged) { }
    public record TakeoverRequest(String projectId,String approvalId,long expectedRevision,String requestKey) { }
    @GetMapping("/operations/takeovers")
    public Map<String,Object> takeovers(@RequestParam String projectId){return operations.takeovers(projectId,SessionUtils.getUserId());}
    @PostMapping("/operations/{id}/resume")
    public Map<String,Object> resume(@PathVariable String id,@RequestBody OperationRequest request){return operations.resume(request.projectId(),id,request.expectedRevision(),request.requestKey(),SessionUtils.getUserId());}
    @PostMapping("/operations/{id}/terminate")
    public Map<String,Object> terminate(@PathVariable String id,@RequestBody OperationRequest request){return operations.terminate(request.projectId(),id,request.expectedRevision(),SessionUtils.getUserId());}
    @PostMapping("/operations/{id}/takeover-preview")
    public Map<String,Object> takeoverPreview(@PathVariable String id,@RequestBody TakeoverPreviewRequest request){return operations.takeoverPreview(request.projectId(),id,request.expectedRevision(),request.newActor(),SessionUtils.getUserId());}
    @PostMapping("/operations/takeover-approve")
    public Map<String,Object> approveTakeover(@RequestBody TakeoverApproveRequest request){return operations.approve(request.projectId(),request.previewId(),request.acknowledged(),SessionUtils.getUserId());}
    @PostMapping("/operations/takeover")
    public Map<String,Object> takeover(@RequestBody TakeoverRequest request){return operations.takeover(request.projectId(),request.approvalId(),request.expectedRevision(),request.requestKey(),SessionUtils.getUserId());}
    public record NewReviewPreviewRequest(String planId,String requestKey,Map<String,Object> draft) { }
    public record NewReviewRecoverRequest(String projectId,String operationId) { }
    @PostMapping("/new-review/recover-preview")
    public Map<String,Object> recoverNewReview(@RequestBody NewReviewRecoverRequest request){return service.recoverNewReview(request.projectId(),request.operationId(),SessionUtils.getUserId());}

    @GetMapping("/new-review/candidates")
    public Map<String,Object> newReviewCandidates(@RequestParam String projectId,@RequestParam(defaultValue="") String keyword,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){
        return service.newReviewCandidates(projectId,keyword,page,size,SessionUtils.getUserId());
    }
    @PostMapping("/new-review/preview")
    public Map<String,Object> newReviewPreview(@RequestBody NewReviewPreviewRequest request){
        return service.previewNewReview(request.planId(),request.requestKey(),request.draft(),"WEB",SessionUtils.getUserId());
    }
    @PostMapping("/new-review/confirm")
    public Map<String,Object> newReviewConfirm(@RequestBody ConfirmRequest request){
        return service.confirmNewReview(request.previewId(),request.requestKey(),request.acknowledged(),SessionUtils.getUserId());
    }

    @GetMapping("/{side}/{id}")
    public Map<String, Object> summary(@PathVariable String side, @PathVariable String id) {
        return service.summary(side, id, SessionUtils.getUserId());
    }

    @PostMapping("/candidates")
    public Map<String, Object> candidates(@RequestBody CandidateRequest request) {
        return service.candidates(request.side(), request.id(), request.keyword(), request.page(), request.size(), SessionUtils.getUserId());
    }

    @GetMapping("/opinion-epochs/{reviewId}")
    public Map<String, Long> opinionEpochs(@PathVariable String reviewId) {
        return service.opinionEpochs(reviewId, SessionUtils.getUserId());
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody PreviewRequest request) {
        return service.preview(request.planId(), request.reviewId(), request.purpose(), SessionUtils.getUserId());
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestBody ConfirmRequest request) {
        return service.confirm(request.previewId(), request.requestKey(), request.acknowledged(), SessionUtils.getUserId());
    }

    @PostMapping("/unlink")
    public Map<String, Object> unlink(@RequestBody UnlinkRequest request) {
        return service.unlink(request.relationId(), request.expectedGeneration(), request.requestKey(), SessionUtils.getUserId());
    }

    @GetMapping("/grant")
    public Map<String, Object> grant(@RequestParam String projectId) {
        return service.grant(projectId, null, null, SessionUtils.getUserId());
    }

    @PostMapping("/grant")
    public Map<String, Object> grant(@RequestBody GrantRequest request) {
        return service.grant(request.projectId(), request.expectedRevision(), request.enabled(), SessionUtils.getUserId());
    }

    @PostMapping("/grant/revoke")
    public Map<String,Object> revoke(@RequestBody GrantRequest request) {
        return service.grant(request.projectId(),request.expectedRevision(),false,SessionUtils.getUserId());
    }

    @GetMapping("/operations")
    public Map<String, Object> operations(@RequestParam String projectId, @RequestParam(required = false) String id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,@RequestParam(defaultValue="false") boolean allProject,@RequestParam(required=false) String sourceKind,@RequestParam(required=false) String operationType,@RequestParam(required=false) String requestKey,@RequestParam(required=false) String relationId,@RequestParam(required=false) String generation) {
        return service.operations(projectId, id, SessionUtils.getUserId(), page, size,allProject,sourceKind,operationType,requestKey,relationId,generation);
    }

    @GetMapping("/operations/{id}")
    public Map<String,Object> operation(@PathVariable String id,@RequestParam String projectId){
        var page=service.operations(projectId,id,SessionUtils.getUserId(),1,1);
        var items=(java.util.List<?>)page.get("items");
        if(items.isEmpty())throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"Operation inaccessible");
        return (Map<String,Object>)items.getFirst();
    }

    @GetMapping("/preview/{id}/items")
    public Map<String, Object> previewItems(@PathVariable String id, @RequestParam String kind,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return service.previewItems(id, kind, page, size, SessionUtils.getUserId());
    }

    public record LifecyclePreviewRequest(String side, String id, String action) { }
    public record LifecycleConfirmRequest(String previewId, boolean acknowledged) { }
    @PostMapping("/lifecycle/preview")
    public Map<String,Object> lifecyclePreview(@RequestBody LifecyclePreviewRequest request) {
        return lifecycle.preview(request.side(), request.id(), request.action(), SessionUtils.getUserId());
    }
    @GetMapping("/frozen/{side}/{id}")
    public Map<String,Object> frozen(@PathVariable String side,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return lifecycle.frozen(side,id,SessionUtils.getUserId(),page,size);}
    @PostMapping("/lifecycle/confirm")
    public Map<String,Object> lifecycleConfirm(@RequestBody LifecycleConfirmRequest request) {
        return lifecycle.confirm(request.previewId(), request.acknowledged(), SessionUtils.getUserId());
    }

    @GetMapping("/history/{reviewId}")
    public Map<String, Object> history(@PathVariable String reviewId, @RequestParam(required = false) String caseId,
            @RequestParam(required = false) Long epoch, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.history(reviewId, caseId, epoch, page, size, SessionUtils.getUserId());
    }

    @ExceptionHandler(PlanReviewFailure.class)
    @io.metersphere.system.controller.handler.annotation.NoResultHolder
    public ResponseEntity<Map<String, Object>> failure(PlanReviewFailure failure) {
        return ResponseEntity.status(failure.httpStatus()).body(Map.of("code", failure.code(), "message", failure.getMessage(), "retryable", false));
    }
}
