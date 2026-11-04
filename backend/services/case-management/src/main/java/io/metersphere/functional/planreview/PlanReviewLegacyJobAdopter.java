package io.metersphere.functional.planreview;
import java.util.Map;

/** Native import module keeps the original source, object identities and committed item flags. */
public interface PlanReviewLegacyJobAdopter {
    Map<String,Object> preview(String project,Map<String,Object> legacyJob,PlanReviewLegacyDecision.Job decision);
    Map<String,Object> adopt(String project,String changeId,Map<String,Object> legacyJob,Map<String,Object> prepared,
                             Map<String,Object> relation,String approvedBy);
    void stop(String project,String changeId);
    PlanReviewLegacyJobAdopter forDatabase(org.springframework.jdbc.core.JdbcTemplate isolated);
    default void prepareSandbox(Map<String,Object> snapshot) { }
}
