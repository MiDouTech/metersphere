package io.metersphere.functional.planreview;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Durable effective-source watermark, including absent identities. The caller saves this inside
 * the same transaction as source membership changes, including soft deletion/recovery.
 */
public record PlanReviewSourceMembership(long version, Set<String> present, Map<String, Long> epochs) {
    public PlanReviewSourceMembership {
        if (version < 0) throw new IllegalArgumentException("源版本无效");
        present = Set.copyOf(present);
        epochs = Map.copyOf(epochs);
        Map<String, Long> validatedEpochs = epochs;
        if (present.stream().anyMatch(id -> id.isBlank() || !validatedEpochs.containsKey(id))
                || epochs.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getValue() < 1)) {
            throw new IllegalArgumentException("源成员分段不完整");
        }
    }

    public static PlanReviewSourceMembership empty() {
        return new PlanReviewSourceMembership(0, Set.of(), Map.of());
    }

    /** Effective IDs must be obtained under the source lock across all FUNCTIONAL collections. */
    public PlanReviewSourceMembership changed(long expectedVersion, Collection<String> effectiveIds) {
        if (version != expectedVersion) throw new IllegalStateException("源版本已变化，请重读");
        Objects.requireNonNull(effectiveIds);
        if (effectiveIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("源成员ID无效");
        }
        Set<String> next = new TreeSet<>(effectiveIds);
        if (next.equals(present)) return this;
        Map<String, Long> updated = new HashMap<>(epochs);
        for (String id : next) if (!present.contains(id)) {
            updated.put(id, Math.addExact(updated.getOrDefault(id, 0L), 1));
        }
        return new PlanReviewSourceMembership(Math.addExact(version, 1), next, updated);
    }
}
