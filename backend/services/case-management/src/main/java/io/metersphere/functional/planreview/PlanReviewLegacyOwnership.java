package io.metersphere.functional.planreview;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Only describes occupancy of already authorized objects; never returns the old counterpart. */
final class PlanReviewLegacyOwnership {
    private PlanReviewLegacyOwnership() { }

    static Set<String> blockedIds(JdbcTemplate jdbc, boolean planSide, Collection<String> ids) {
        if (ids.isEmpty()) return Set.of();
        String column = planSide ? "plan_id" : "review_id";
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        var blocked = new HashSet<>(jdbc.queryForList("SELECT " + column
                + " FROM functional_import_followup_group WHERE " + column + " IN (" + placeholders + ")",
                String.class, ids.toArray()));
        blocked.addAll(jdbc.queryForList("SELECT " + column
                + " FROM functional_import_followup WHERE policy<>'IMPORT_ONLY' AND state<>'MIGRATED_V2' AND "
                + column + " IN (" + placeholders + ")", String.class, ids.toArray()));
        return blocked;
    }
}
