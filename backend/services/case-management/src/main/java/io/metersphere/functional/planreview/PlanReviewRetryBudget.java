package io.metersphere.functional.planreview;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Serializable value for each independent member-sync / notification retry round. */
public record PlanReviewRetryBudget(int completedAttempts, Instant nextAttemptAt, boolean exhausted) {
    private static final int MAX_ATTEMPTS = 4; // one initial attempt and three automatic retries
    private static final long[] BACKOFF_SECONDS = {10, 30, 60};

    public PlanReviewRetryBudget {
        if (completedAttempts < 0 || completedAttempts > MAX_ATTEMPTS
                || exhausted != (completedAttempts == MAX_ATTEMPTS)) {
            throw new IllegalArgumentException("重试预算无效");
        }
        if (exhausted && nextAttemptAt != null) throw new IllegalArgumentException("预算耗尽不能继续排队");
        if (!exhausted) Objects.requireNonNull(nextAttemptAt);
    }

    public static PlanReviewRetryBudget initial(Instant now) {
        return new PlanReviewRetryBudget(0, now, false);
    }

    public boolean due(Instant now) { return !exhausted && !now.isBefore(nextAttemptAt); }

    public boolean late(Instant now) {
        return !exhausted && completedAttempts > 0 && now.isAfter(nextAttemptAt.plusSeconds(5));
    }

    /** finishedAt is the end of the failed attempt, not its start or the source change time. */
    public PlanReviewRetryBudget failed(Instant finishedAt) {
        if (exhausted) throw new IllegalStateException("自动重试已耗尽，需有权人工恢复");
        int completed = completedAttempts + 1;
        return completed == MAX_ATTEMPTS
                ? new PlanReviewRetryBudget(completed, null, true)
                : new PlanReviewRetryBudget(completed,
                        finishedAt.plus(Duration.ofSeconds(BACKOFF_SECONDS[completed - 1])), false);
    }

    /** New source events only advance the source watermark; they NEVER create a fresh retry round. */
    public PlanReviewRetryBudget sourceChanged() { return this; }
}
