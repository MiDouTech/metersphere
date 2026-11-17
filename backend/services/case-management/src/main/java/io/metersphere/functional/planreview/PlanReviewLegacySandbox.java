package io.metersphere.functional.planreview;
import java.util.Map;

/** A separate explicitly configured database is mandatory; never falls back to the project DB. */
public interface PlanReviewLegacySandbox {
    Map<String,Object> rehearse(String project,String changeId,Map<String,Object> snapshot,
                               PlanReviewLegacyDecision selection,PlanReviewLegacyRehearsalProgram program);
    interface PlanReviewLegacyRehearsalProgram {
        Map<String,Object> run(org.springframework.jdbc.core.JdbcTemplate isolated,Map<String,Object> snapshot,
                               PlanReviewLegacyDecision selection);
    }
}
