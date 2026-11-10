package io.metersphere.functional.planreview;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Product v1.0 rules shared by HTTP, imports and workers; never grants permissions. */
public final class PlanReviewPolicy {
    public static final Duration PREVIEW_TTL = Duration.ofMinutes(10);
    public static final Duration CONSISTENCY_TARGET = Duration.ofSeconds(30);
    public static final Duration NOTICE_TARGET = Duration.ofSeconds(60);

    private PlanReviewPolicy() { }

    public enum Permission {
        PLAN_READ, PLAN_UPDATE, PLAN_ADD, PLAN_MEMBERS,
        REVIEW_READ, REVIEW_UPDATE, REVIEW_ADD, REVIEW_MEMBERS, REVIEW_OPINION,
        IMPORT, RESTORE_EDIT, GRANT_MANAGE, NOTICE_RECOVER, TAKEOVER_APPROVE, LEGACY_MIGRATE
    }

    public enum Action {
        PAIR, UNLINK, CREATE_PAIR, CREATE_LINKED_REVIEW, APPEND,
        RETRY_SYNC, RESTORE_PLAN, RESTORE_REVIEW, MANAGE_GRANT, RETRY_NOTICE
    }

    public enum State {
        PREPARED, UNDERWAY, COMPLETED, ARCHIVED, DELETED, UNKNOWN;

        public boolean editable() { return this == PREPARED || this == UNDERWAY; }

        /** Unknown historical spellings must be inventoried, not silently normalized. */
        public static State parse(String value) {
            if (value == null) return UNKNOWN;
            try { return valueOf(value); }
            catch (IllegalArgumentException ignored) { return UNKNOWN; }
        }
    }

    public record Access(Set<Permission> permissions, boolean serviceGrant) {
        public Access { permissions = Set.copyOf(permissions); }

        public boolean hasAll(Set<Permission> required) { return permissions.containsAll(required); }
    }

    public static Set<Permission> required(Action action) {
        return switch (action) {
            case PAIR, UNLINK -> EnumSet.of(Permission.PLAN_READ, Permission.PLAN_UPDATE,
                    Permission.REVIEW_READ, Permission.REVIEW_UPDATE);
            case CREATE_PAIR -> EnumSet.of(Permission.PLAN_ADD, Permission.REVIEW_ADD,
                    Permission.PLAN_READ, Permission.REVIEW_READ);
            case CREATE_LINKED_REVIEW -> EnumSet.of(Permission.REVIEW_ADD, Permission.PLAN_READ,
                    Permission.PLAN_UPDATE, Permission.REVIEW_READ);
            case APPEND -> EnumSet.of(Permission.IMPORT, Permission.PLAN_READ, Permission.REVIEW_READ,
                    Permission.PLAN_MEMBERS, Permission.REVIEW_MEMBERS);
            case RETRY_SYNC -> EnumSet.of(Permission.PLAN_READ, Permission.REVIEW_READ,
                    Permission.PLAN_UPDATE, Permission.REVIEW_MEMBERS);
            case RESTORE_PLAN -> EnumSet.of(Permission.PLAN_READ, Permission.REVIEW_READ,
                    Permission.PLAN_UPDATE, Permission.RESTORE_EDIT);
            case RESTORE_REVIEW -> EnumSet.of(Permission.PLAN_READ, Permission.REVIEW_READ,
                    Permission.REVIEW_UPDATE, Permission.RESTORE_EDIT);
            case MANAGE_GRANT -> EnumSet.of(Permission.GRANT_MANAGE);
            case RETRY_NOTICE -> EnumSet.of(Permission.NOTICE_RECOVER);
        };
    }

    public static boolean allowed(Action action, Access access) {
        return access.hasAll(required(action));
    }

    public static boolean allowedCreatePair(Access access, int initialMemberCount) {
        if (initialMemberCount < 0) throw new IllegalArgumentException("初始成员数无效");
        return allowed(Action.CREATE_PAIR, access)
                && (initialMemberCount == 0 || access.permissions().contains(Permission.PLAN_MEMBERS));
    }

    /** Callers must additionally enforce original source operation permissions/project access. */
    public static boolean canWriteSource(Access access, State plan) {
        return plan.editable() && access.permissions().contains(Permission.PLAN_MEMBERS);
    }

    public static boolean canSynchronize(State plan, State review, boolean active, boolean grant) {
        return active && plan.editable() && review.editable();
    }

    /** First restore changes only one side; no member or grant approval is implied. */
    public static State restoredState(State current) {
        return switch (current) {
            case ARCHIVED -> State.COMPLETED;
            case COMPLETED -> State.UNDERWAY;
            default -> throw new IllegalArgumentException("该状态不允许恢复编辑");
        };
    }

    public record Revision(String projectId, String planId, String reviewId, String generation,
                           long sourceVersion, long targetRevision, long grantRevision) {
        public Revision {
            Objects.requireNonNull(projectId);
            Objects.requireNonNull(planId);
            Objects.requireNonNull(reviewId);
            Objects.requireNonNull(generation);
            if (sourceVersion < 0 || targetRevision < 0 || grantRevision < 0) {
                throw new IllegalArgumentException("版本不能为负数");
            }
            // Retain the wire/storage field for older previews; it no longer gates work.
            grantRevision = 0;
        }
    }

    public record Preview(Revision revision, Instant expiresAt) {
        public Preview { Objects.requireNonNull(revision); Objects.requireNonNull(expiresAt); }
        public boolean valid(Revision current, Instant now) {
            return now.isBefore(expiresAt) && revision.equals(current);
        }
    }
}
