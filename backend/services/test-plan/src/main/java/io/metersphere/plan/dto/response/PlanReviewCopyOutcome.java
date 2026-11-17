package io.metersphere.plan.dto.response;

import java.util.List;

public record PlanReviewCopyOutcome(String requestId,String operationId,long operationCount,long createdCount,long failedCount,List<Item> items) {
    public record Item(String sourceId,String sourceType,String sourceName,Long sourceNum,String operationId,String planId,String reviewId,String stage,String state,String syncState,boolean created,String safeReason) { }
}
