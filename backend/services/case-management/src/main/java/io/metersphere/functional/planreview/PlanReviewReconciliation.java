package io.metersphere.functional.planreview;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Pure difference calculation. Persistence must atomically revalidate revisions before applying it. */
public final class PlanReviewReconciliation {
    private PlanReviewReconciliation() { }

    public record Difference(Set<String> added, Set<String> removed, Set<String> retained,
                             Set<String> reentered) {
        public Difference {
            added = immutable(added); removed = immutable(removed);
            retained = immutable(retained); reentered = immutable(reentered);
        }
        public boolean empty() { return added.isEmpty() && removed.isEmpty() && reentered.isEmpty(); }
    }

    /**
     * Input is effective FUNCTIONAL case IDs across every collection, not execution rows/API IDs.
     * Source membership epochs are durable and advance only on a genuine absent -> present edge.
     * A frozen remove/readd must reset review even when the final set equals its frozen snapshot.
     */
    public static Difference calculate(Collection<String> effectiveSourceIds, Set<String> currentReviewIds,
                                       Map<String, Long> sourceEpochs, Map<String, Long> appliedEpochs) {
        Set<String> source = checked(effectiveSourceIds);
        Set<String> current = checked(currentReviewIds);
        TreeSet<String> added = new TreeSet<>(source); added.removeAll(current);
        TreeSet<String> removed = new TreeSet<>(current); removed.removeAll(source);
        TreeSet<String> retained = new TreeSet<>(source); retained.retainAll(current);
        TreeSet<String> reentered = new TreeSet<>();
        for (String id : retained) {
            Long sourceEpoch = sourceEpochs.get(id), appliedEpoch = appliedEpochs.get(id);
            if (sourceEpoch == null || appliedEpoch == null || sourceEpoch < 0 || appliedEpoch < 0) {
                throw new IllegalArgumentException("成员分段信息缺失，需先盘点历史");
            }
            if (sourceEpoch < appliedEpoch) throw new IllegalArgumentException("源成员分段倒退");
            if (sourceEpoch > appliedEpoch) reentered.add(id);
        }
        retained.removeAll(reentered);
        return new Difference(added, removed, retained, reentered);
    }

    private static Set<String> checked(Collection<String> ids) {
        if (ids == null || ids.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("成员ID无效");
        }
        return new TreeSet<>(ids);
    }

    private static Set<String> immutable(Set<String> ids) {
        return Collections.unmodifiableSet(new TreeSet<>(ids));
    }
}
