package io.metersphere.functional.planreview;

import java.util.Objects;

/**
 * Durable approval barrier, not an authorization token. Store/reload alongside the relation.
 * A transaction must lock relation, grant and both objects, authorize the current actor, then
 * revalidate this barrier before committing each batch. These methods do no database writes.
 */
public record PlanReviewApproval(PlanReviewPolicy.Revision approved, Status status, long appliedBatches) {
    public enum Status { APPLYING, RECONFIRM_REQUIRED, COMPLETE, ENDED }

    public PlanReviewApproval {
        Objects.requireNonNull(approved);
        Objects.requireNonNull(status);
        if (appliedBatches < 0) throw new IllegalArgumentException("批次进度无效");
    }

    public static PlanReviewApproval begin(PlanReviewPolicy.Revision current) {
        return new PlanReviewApproval(current, Status.APPLYING, 0);
    }

    /** External source/member-opinion changes pause incomplete approval; retain generation. */
    public PlanReviewApproval observe(PlanReviewPolicy.Revision current) {
        if (status != Status.APPLYING || approved.equals(current)) return this;
        if (!sameRelation(current)) return ended();
        return new PlanReviewApproval(approved, Status.RECONFIRM_REQUIRED, appliedBatches);
    }

    public boolean mayCommit(PlanReviewPolicy.Revision current) {
        return status == Status.APPLYING && approved.equals(current);
    }

    /**
     * Advance only after a batch's members, historical epochs, statistics and progress commit
     * atomically. Self-produced revision changes advance the approval, not invalidate it.
     */
    public PlanReviewApproval committedBatch(PlanReviewPolicy.Revision before,
                                            PlanReviewPolicy.Revision after, boolean converged) {
        if (!mayCommit(before) || !sameRelation(after)
                || after.sourceVersion() != before.sourceVersion()
                || after.targetRevision() < before.targetRevision()) {
            throw new IllegalStateException("批准已变化，禁止提交旧批次");
        }
        return new PlanReviewApproval(after, converged ? Status.COMPLETE : Status.APPLYING,
                Math.addExact(appliedBatches, 1));
    }

    /** The caller must separately require M08, even if the caller originally satisfied M02. */
    public PlanReviewApproval reconfirm(PlanReviewPolicy.Preview preview,
                                       PlanReviewPolicy.Revision current, java.time.Instant now) {
        if (status != Status.RECONFIRM_REQUIRED || !sameRelation(current) || !preview.valid(current, now)) {
            throw new IllegalStateException("请重新预览当前关系");
        }
        return new PlanReviewApproval(current, Status.APPLYING, appliedBatches);
    }

    public PlanReviewApproval ended() {
        return new PlanReviewApproval(approved, Status.ENDED, appliedBatches);
    }

    private boolean sameRelation(PlanReviewPolicy.Revision other) {
        return approved.projectId().equals(other.projectId()) && approved.planId().equals(other.planId())
                && approved.reviewId().equals(other.reviewId()) && approved.generation().equals(other.generation());
    }
}
