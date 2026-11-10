package io.metersphere.functional.planreview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Holds the derived completion state while linked membership is unresolved; execution results remain saved. */
@Service
public class PlanReviewCompletionGuard {
    private final JdbcTemplate jdbc;
    private final PlanReviewPlanStateResolver states;
    public PlanReviewCompletionGuard(JdbcTemplate jdbc){this(jdbc,new PlanReviewPlanStateResolver(List.of()));}
    @org.springframework.beans.factory.annotation.Autowired
    public PlanReviewCompletionGuard(JdbcTemplate jdbc,PlanReviewPlanStateResolver states){this.jdbc=jdbc;this.states=states;}
    public String executionRevision(String plan){
        var clock=jdbc.queryForList("SELECT version FROM plan_review_source_clock WHERE plan_id=?",Long.class,plan);
        String value=states.executionRevision(plan)+":"+(clock.isEmpty()?0:clock.getFirst());
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public boolean reopened(String plan){return jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_lifecycle_preview WHERE object_side='plan' AND object_id=? AND action='RESTORE_EDIT' AND consumed_at IS NOT NULL",Long.class,plan)>0;}
    public boolean reopenUnchanged(String plan){
        var rows=jdbc.queryForList("SELECT source_execution_revision FROM plan_review_lifecycle_preview WHERE object_side='plan' AND object_id=? AND action='RESTORE_EDIT' AND consumed_at IS NOT NULL ORDER BY consumed_at DESC,id DESC LIMIT 1",String.class,plan);
        return rows.size()==1 && Objects.equals(rows.getFirst(),executionRevision(plan));
    }
    public boolean linked(String plan){return jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation WHERE active_plan_id=? AND state='ACTIVE'",Long.class,plan)>0;}
    public String visibleStatus(String plan,String calculated){
        var raw=jdbc.queryForList("SELECT status FROM test_plan WHERE id=?",String.class,plan);
        if(raw.size()!=1)return "UNKNOWN";
        if(Set.of("COMPLETED","ARCHIVED").contains(raw.getFirst()))return raw.getFirst();
        if(reopenUnchanged(plan))return "UNDERWAY";
        if("COMPLETED".equals(calculated) && (!permit(plan)||linked(plan)||reopened(plan)))return "UNDERWAY";
        return calculated;
    }
    public boolean permit(String plan){
        var rows=jdbc.queryForList("SELECT r.* FROM plan_review_relation r WHERE r.active_plan_id=? AND r.state='ACTIVE'",plan);
        if(rows.isEmpty())return true;
        var row=rows.getFirst();
        if(!"SYNCED".equals(row.get("sync_state")) || !Objects.equals(row.get("source_version"),row.get("applied_version")))return false;
        var clocks=jdbc.queryForList("SELECT version FROM plan_review_source_clock WHERE plan_id=?",Long.class,plan);
        if(clocks.size()!=1 || ((Number)row.get("applied_version")).longValue()!=clocks.getFirst())return false;
        var source=new TreeSet<>(jdbc.queryForList("SELECT DISTINCT m.functional_case_id FROM test_plan_functional_case m JOIN functional_case c ON CAST(c.id AS BINARY(50))=CAST(m.functional_case_id AS BINARY(50)) WHERE m.test_plan_id=? AND c.project_id=? AND c.deleted=0",String.class,plan,row.get("project_id")));
        var target=new TreeSet<>(jdbc.queryForList("SELECT case_id FROM case_review_functional_case WHERE review_id=?",String.class,row.get("review_id")));
        return source.equals(target);
    }
}
