package io.metersphere.functional.handoff;

import io.metersphere.functional.planreview.*;
import io.metersphere.sdk.util.JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Reads the MSP-12 authority; never creates a second plan/review relationship. Caller holds its source lock. */
@Service
public class HandoffSourceReader {
    public record Source(String project, String plan, String review, String planNumber, String planName,
                         String reviewNumber, String reviewName, long frozenAt, String fingerprint, HandoffPolicy.Snapshot snapshot, String snapshotId, String ruleRevision, String snapshotHash) { }
    private final JdbcTemplate jdbc;
    private final PlanReviewPlanStateResolver states;
    private final PlanReviewAuthorization authorization;
    private final PlanReviewFrozenReader frozen;
    public HandoffSourceReader(JdbcTemplate jdbc, PlanReviewPlanStateResolver states,
                               PlanReviewAuthorization authorization, PlanReviewFrozenReader frozen) {
        this.jdbc = jdbc; this.states = states; this.authorization = authorization; this.frozen = frozen;
    }
    public Source read(String project, String plan) {
        var plans = jdbc.queryForList("SELECT * FROM test_plan WHERE id=? AND project_id=? AND type='TEST_PLAN'", plan, project);
        if (plans.size() != 1) throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE", 404, "测试计划不可访问");
        var p = states.resolve(plans.getFirst());
        var relations = jdbc.queryForList("SELECT * FROM plan_review_relation WHERE project_id=? AND active_plan_id=? AND state='ACTIVE'", project, plan);
        var relation = relations.size() == 1 ? relations.getFirst() : Map.<String,Object>of();
        String reviewId = text(relation, "review_id");
        var reviews = jdbc.queryForList("SELECT * FROM case_review WHERE id=? AND project_id=?", reviewId, project);
        var review = reviews.size() == 1 ? reviews.getFirst() : Map.<String,Object>of();
        var source = new TreeMap<String, Long>();
        jdbc.queryForList("SELECT case_id,epoch FROM plan_review_source_member WHERE plan_id=? AND present=1 ORDER BY case_id", plan)
                .forEach(m -> source.put(text(m, "case_id"), number(m, "epoch")));
        var current = new TreeMap<String, HandoffPolicy.Member>();
        jdbc.queryForList("SELECT m.case_id,m.status,t.source_epoch FROM case_review_functional_case m LEFT JOIN plan_review_target_member t ON t.review_id=m.review_id AND t.case_id=m.case_id AND t.present=1 WHERE m.review_id=? ORDER BY m.case_id", reviewId)
                .forEach(m -> { if (current.put(text(m, "case_id"), new HandoffPolicy.Member(number(m, "source_epoch"), text(m, "status"))) != null)
                    throw new PlanReviewFailure("SOURCE_INCONSISTENT",409,"评审成员不唯一，交接暂停"); });
        var frame = review.isEmpty() ? Map.<String,Object>of() : frozen.frame("review", reviewId);
        var members = new TreeMap<String, HandoffPolicy.Member>();
        Map<?,?> data = frame.get("data") instanceof Map<?,?> map ? map : Map.of();
        var epochs = new TreeMap<String, Long>();
        if (data.get("source") instanceof List<?> list) for (Object item : list) if (item instanceof Map<?,?> m)
            epochs.put(Objects.toString(m.get("case_id"), ""), asLong(m.get("epoch")));
        if (data.get("members") instanceof List<?> list) for (Object item : list) if (item instanceof Map<?,?> m) {
            String id = Objects.toString(m.get("case_id"), "");
            members.put(id, new HandoffPolicy.Member(epochs.getOrDefault(id, -1L), Objects.toString(m.get("status"), "")));
        }
        String opinions = new PlanReviewFingerprintReader(jdbc).currentForSnapshot(reviewId, text(frame,"target_fingerprint"));
        boolean ruleMatches = data.get("review") instanceof Map<?,?> old && Objects.equals(old.get("review_pass_rule"), review.get("review_pass_rule"));
        boolean authorized = false;
        try {
            var access = authorization.current(text(relation, "original_actor"), project, false);
            authorized = access.serviceGrant() && access.permissions().containsAll(Set.of(PlanReviewPolicy.Permission.PLAN_READ, PlanReviewPolicy.Permission.REVIEW_READ));
        } catch (PlanReviewFailure unavailable) { /* No fallback identity. */ }
        var snapshot = new HandoffPolicy.Snapshot(text(p,"status"), text(review,"status"), relations.size()==1 && reviews.size()==1,
                authorized, text(relation,"generation"), text(frame,"generation"), number(relation,"source_version"),
                number(relation,"applied_version"), number(frame,"source_version"),
                truth(relation.get("approval_pending")) || !Set.of("SYNCED","FROZEN").contains(text(relation,"sync_state")) || !ruleMatches,
                source, current, members, opinions, text(frame,"target_fingerprint"));
        String fingerprint = HandoffPolicy.hash(JSON.toJSONString(List.of(snapshot,text(frame,"id"),text(review,"review_pass_rule"))));
        return new Source(project,plan,reviewId,text(p,"num"),text(p,"name"),text(review,"num"),text(review,"name"),number(frame,"create_time"),fingerprint,snapshot,text(frame,"id"),HandoffPolicy.hash(text(review,"review_pass_rule")),HandoffPolicy.hash(JSON.toJSONString(data)));
    }
    static String text(Map<?,?> row,String key) { return Objects.toString(row.get(key), ""); }
    static long number(Map<?,?> row,String key) { return asLong(row.get(key)); }
    static long asLong(Object value) { return value instanceof Number n ? n.longValue() : 0; }
    static boolean truth(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue()!=0 || value instanceof byte[] b && b.length>0 && b[0]!=0; }
}
