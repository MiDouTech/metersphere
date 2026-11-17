package io.metersphere.functional.planreview;
import java.util.LinkedHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Source/target revision fence. Called within the existing business transaction, never async. */
@Service
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public class PlanReviewPersistence {
    private final JdbcTemplate jdbc;
    private java.util.List<PlanReviewCurrentActor> actors=java.util.List.of();
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCurrentActors(java.util.List<PlanReviewCurrentActor> actors){this.actors=java.util.List.copyOf(actors);}

    public PlanReviewPersistence(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void lockProject(String project){
        jdbc.update("INSERT IGNORE INTO plan_review_grant(project_id,update_time) VALUES(?,?)",project,now());
        jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE",project);
    }
    /** Old jobs may still own these exact objects. Only the explicit migration protocol may adopt them. */
    public void requireNoLegacyOwnership(String project,String plan,String review){
        lockProject(project);
        var groups=jdbc.queryForList("SELECT id FROM functional_import_followup_group WHERE plan_id=? OR review_id=? ORDER BY id FOR UPDATE",String.class,plan,review);
        var jobs=jdbc.queryForList("SELECT job_id FROM functional_import_followup WHERE policy<>'IMPORT_ONLY' AND state<>'MIGRATED_V2' AND (plan_id=? OR review_id=?) ORDER BY job_id FOR UPDATE",String.class,plan,review);
        if(!groups.isEmpty()||!jobs.isEmpty())throw new PlanReviewFailure("LEGACY_ADOPTION_REQUIRED",409,"历史关联或任务尚未完成受控交接，请先核查迁移清单；不会另建评审或改变原任务目标");
    }
    /** Module deletion shares the creation mutex and validates ownership using current rows. */
    public void lockModules(String table,String project,List<String> ids){
        if(!Set.of("test_plan_module","case_review_module").contains(table))throw new IllegalArgumentException("Unsupported module table");
        lockProject(project);
        var unique=new java.util.TreeSet<>(ids);var found=new java.util.HashSet<String>();
        var ordered=List.copyOf(unique);
        for(int start=0;start<ordered.size();start+=200){
            var batch=ordered.subList(start,Math.min(start+200,ordered.size()));
            var args=new java.util.ArrayList<Object>();args.add(project);args.addAll(batch);
            found.addAll(jdbc.queryForList("SELECT id FROM "+table+" WHERE project_id=? AND id IN ("+String.join(",",java.util.Collections.nCopies(batch.size(),"?"))+") ORDER BY id FOR UPDATE",String.class,args.toArray()));
        }
        if(!found.equals(unique))throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"目录不存在或不在当前项目，未执行删除");
    }

    /** Capture the pre-mutation effective view before a shared case soft-delete/recovery. */
    public List<String> beforeCaseMutation(List<String> caseIds) {
        var plans = new java.util.TreeSet<String>();
        if (caseIds == null || caseIds.isEmpty()) return List.of();
        // Case ownership is immutable. Lock its project before reading current plan memberships,
        // so a pre-existing RR snapshot cannot miss a newly committed source reference.
        var projects=new java.util.TreeSet<String>();
        for (int start = 0; start < caseIds.size(); start += 200) {
            var chunk = caseIds.subList(start, Math.min(start + 200, caseIds.size()));
            String markers = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            projects.addAll(jdbc.queryForList("SELECT project_id FROM functional_case WHERE id IN ("+markers+")",String.class,chunk.toArray()));
        }
        for(String project:projects){
            jdbc.update("INSERT IGNORE INTO plan_review_grant(project_id,update_time) VALUES(?,?)",project,now());
            jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE",project);
        }
        for (int start = 0; start < caseIds.size(); start += 200) {
            var chunk = caseIds.subList(start, Math.min(start + 200, caseIds.size()));
            String markers = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            plans.addAll(jdbc.queryForList("SELECT m.test_plan_id FROM test_plan_functional_case m JOIN test_plan p ON p.id=m.test_plan_id JOIN functional_case c ON c.id=m.functional_case_id AND c.project_id=p.project_id WHERE m.functional_case_id IN (" + markers + ") ORDER BY m.test_plan_id FOR UPDATE", String.class, chunk.toArray()));
        }
        for (String plan : plans) sourceChanged(plan);
        return List.copyOf(plans);
    }

    public void afterCaseMutation(List<String> plans) { for (String plan : plans) sourceMutated(plan); }
    /** Only actual mutation hooks may attribute a changed source to the current caller. */
    public PlanReviewSourceMembership sourceMutated(String plan){
        var current=actors.stream().map(PlanReviewCurrentActor::current).filter(java.util.Objects::nonNull).filter(value->!value.isBlank()).distinct().toList();
        if(current.size()>1)throw new PlanReviewFailure("ACTOR_CONTEXT_CONFLICT",403,"当前源操作身份不明确，未保存成员变更");
        return sourceChanged(plan,current.isEmpty()?null:current.getFirst());
    }

    public boolean linkedReview(String reviewId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation WHERE active_review_id=? AND state='ACTIVE'", Integer.class, reviewId) > 0;
    }

    /**
     * Source mutation entry: acquire the same order as reconciliation before changing the source.
     * The original application service still owns source authorization and source status checks.
     */
    public void lockSource(String planId) {
        var plans = jdbc.queryForList("SELECT project_id FROM test_plan WHERE id=?", String.class, planId);
        if (plans.isEmpty()) throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE", 404, "测试计划不可访问");
        String project = plans.getFirst();
        jdbc.update("INSERT IGNORE INTO plan_review_grant(project_id,update_time) VALUES(?,?)", project, now());
        jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE", project);
        jdbc.queryForList("SELECT id FROM test_plan WHERE id=? AND project_id=? FOR UPDATE", planId, project);
        List<String> reviews = jdbc.queryForList(
                "SELECT review_id FROM plan_review_relation WHERE active_plan_id=? AND project_id=? FOR UPDATE", String.class, planId, project);
        for (String review : reviews) jdbc.queryForList("SELECT id FROM case_review WHERE id=? FOR UPDATE", review);
        jdbc.queryForList("SELECT id FROM plan_review_relation WHERE active_plan_id=? AND project_id=? FOR UPDATE", planId, project);
    }

    /** Persist all genuine effective-membership edges, even while the target is frozen. */
    public PlanReviewSourceMembership sourceChanged(String planId) {
        return sourceChanged(planId,null);
    }
    /** Background imports pass their current, reauthorized executing actor explicitly. */
    public PlanReviewSourceMembership sourceChanged(String planId,String explicitActor) {
        lockSource(planId);
        String project = jdbc.queryForObject("SELECT project_id FROM test_plan WHERE id=?", String.class, planId);
        jdbc.update("INSERT IGNORE INTO plan_review_source_clock(plan_id,project_id,update_time) VALUES(?,?,?)",
                planId, project, now());
        long version = jdbc.queryForObject("SELECT version FROM plan_review_source_clock WHERE plan_id=? FOR UPDATE", Long.class, planId);
        var rows = jdbc.queryForList("SELECT case_id,epoch,present FROM plan_review_source_member WHERE plan_id=? ORDER BY case_id FOR UPDATE", planId);
        Map<String, Long> epochs = new HashMap<>();
        Set<String> present = new java.util.HashSet<>();
        for (var row : rows) {
            String id = (String) row.get("case_id");
            epochs.put(id, ((Number) row.get("epoch")).longValue());
            if (truth(row.get("present"))) present.add(id);
        }
        var before = new PlanReviewSourceMembership(version, present, epochs);
        var effective = jdbc.queryForList("""
                SELECT m.functional_case_id FROM test_plan_functional_case m
                JOIN functional_case c ON CAST(c.id AS BINARY(50))=CAST(m.functional_case_id AS BINARY(50))
                JOIN test_plan p ON CAST(p.id AS BINARY(50))=CAST(m.test_plan_id AS BINARY(50))
                WHERE m.test_plan_id=? AND p.type='TEST_PLAN' AND c.deleted=0
                  AND CAST(c.project_id AS BINARY(50))=CAST(p.project_id AS BINARY(50)) ORDER BY m.functional_case_id FOR UPDATE
                """, String.class, planId);
        var after = before.changed(version, effective);
        if (after == before) return before;
        for (String id : after.epochs().keySet()) {
            boolean isPresent = after.present().contains(id);
            if (before.present().contains(id) == isPresent && before.epochs().containsKey(id)) continue;
            jdbc.update("""
                    INSERT INTO plan_review_source_member(plan_id,case_id,epoch,present,last_version)
                    VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE epoch=VALUES(epoch),present=VALUES(present),last_version=VALUES(last_version)
                    """, planId, id, after.epochs().get(id), isPresent, after.version());
        }
        jdbc.update("UPDATE plan_review_source_clock SET version=?,update_time=? WHERE plan_id=? AND version=?",
                after.version(), now(), planId, version);
        jdbc.update("""
                UPDATE plan_review_relation SET source_version=?,source_changed_at=?,update_time=?,
                sync_state=CASE WHEN approval_pending=1 THEN 'RECONFIRM_REQUIRED'
                    WHEN sync_state IN ('FAILED_MANUAL','RECONFIRM_REQUIRED') THEN sync_state ELSE 'PENDING' END
                WHERE active_plan_id=? AND project_id=? AND state='ACTIVE'
                """, after.version(), now(), now(), planId, project);
        if(explicitActor!=null){
            var currentActors=java.util.stream.Stream.concat(actors.stream().map(PlanReviewCurrentActor::current),java.util.stream.Stream.of(explicitActor)).filter(java.util.Objects::nonNull).filter(value->!value.isBlank()).distinct().toList();
            if(currentActors.size()>1)throw new PlanReviewFailure("ACTOR_CONTEXT_CONFLICT",403,"当前源操作身份不明确，未保存成员变更");
        }
        // A read/scheduler discovering an untracked change must not be recorded as its source author.
        jdbc.update("UPDATE plan_review_relation SET source_actor=? WHERE active_plan_id=? AND project_id=? AND state='ACTIVE'",explicitActor,planId,project);
        return after;
    }

    /** Guard every independent member entry, not just a hidden UI button. */
    public void requireIndependentReview(String reviewId) {
        lockReview(reviewId);
        requireReviewEditable(reviewId);
        if (!active(reviewId).isEmpty()) throw new PlanReviewFailure("MEMBERSHIP_SOURCE_ONLY", 409, "请到关联测试计划维护用例");
    }

    /**
     * A linked opinion must carry the membership epoch read with the case. Checking this while
     * holding the reconciliation lock makes opinion-before-exit and exit-before-opinion linear.
     */
    public void opinionChanging(String reviewId, Map<String, Long> expectedEpochs, List<String> caseIds) {
        lockReview(reviewId);
        requireReviewEditable(reviewId);
        var relations = active(reviewId);
        if (relations.isEmpty()) return; // independent reviews retain their original API contract
        String status = jdbc.queryForObject("SELECT status FROM case_review WHERE id=?", String.class, reviewId);
        if (!PlanReviewPolicy.State.parse(status).editable()) throw new PlanReviewFailure("OBJECT_LOCKED", 409, "当前评审不可编辑");
        for (String caseId : caseIds.stream().distinct().sorted().toList()) {
            Long expected = expectedEpochs == null ? null : expectedEpochs.get(caseId);
            if (expected == null) throw new PlanReviewFailure("MEMBERSHIP_EPOCH_REQUIRED", 409, "成员版本缺失，请刷新后重试");
            var epochs = jdbc.queryForList("SELECT epoch FROM plan_review_target_member WHERE review_id=? AND case_id=? AND present=1 FOR UPDATE",
                    Long.class, reviewId, caseId);
            if (epochs.size() != 1 || !expected.equals(epochs.getFirst())) {
                throw new PlanReviewFailure("MEMBERSHIP_EPOCH_STALE", 409, "该用例已退出或重新加入当前评审，请刷新后重试");
            }
        }
        jdbc.update("""
                UPDATE plan_review_relation SET opinion_revision=opinion_revision+1,update_time=?,
                sync_state=CASE WHEN approval_pending=1 THEN 'RECONFIRM_REQUIRED' ELSE sync_state END
                WHERE active_review_id=? AND state='ACTIVE'
                """, now(), reviewId);
    }

    private void requireReviewEditable(String review){
        String status=jdbc.queryForObject("SELECT status FROM case_review WHERE id=?",String.class,review);
        if(!PlanReviewPolicy.State.parse(status).editable())throw new PlanReviewFailure("OBJECT_LOCKED",409,"评审已完成、归档或状态待核实，请先恢复本侧编辑");
    }
    public void reviewEditing(String review){
        lockReview(review); requireReviewEditable(review);
        jdbc.update("UPDATE plan_review_relation SET opinion_revision=opinion_revision+1,sync_state=CASE WHEN approval_pending=1 THEN 'RECONFIRM_REQUIRED' ELSE sync_state END,update_time=? WHERE active_review_id=? AND state='ACTIVE'",now(),review);
    }

    public void freezeIndependentPlan(String plan,String state,String actor,Map<String,Object> statistics){
        var object=jdbc.queryForMap("SELECT * FROM test_plan WHERE id=? FOR UPDATE",plan);
        var scope=jdbc.queryForList("SELECT m.case_id,m.epoch,c.num case_num,c.name case_name FROM plan_review_source_member m JOIN functional_case c ON CAST(c.id AS BINARY(50))=CAST(m.case_id AS BINARY(50)) WHERE m.plan_id=? AND m.present=1 AND c.project_id=? AND c.deleted=0",plan,object.get("project_id"));
        var snapshot=Map.of("source",scope,"sourceStatistics",statistics);
        jdbc.update("INSERT INTO plan_review_frozen_snapshot(id,project_id,object_side,object_id,relation_id,generation,object_state,source_version,applied_version,target_fingerprint,snapshot,actor,create_time) VALUES(?,?,'plan',?,NULL,NULL,?,0,0,'independent',?,?,?)",
                java.util.UUID.randomUUID().toString(),object.get("project_id"),plan,state,io.metersphere.sdk.util.JSON.toJSONString(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8),actor,now());
    }

    public void freezeIndependentReview(String reviewId,String state,String actor){
        var review=new LinkedHashMap<>(jdbc.queryForMap("SELECT * FROM case_review WHERE id=?",reviewId));
        Object members=jdbc.queryForList("SELECT m.*,c.num case_num,c.name case_name FROM case_review_functional_case m JOIN functional_case c ON CAST(c.id AS BINARY(50))=CAST(m.case_id AS BINARY(50)) WHERE m.review_id=? AND c.deleted=0 AND c.project_id=?",reviewId,review.get("project_id"));
        Object opinions=jdbc.queryForList("SELECT h.* FROM case_review_history h WHERE h.review_id=? AND h.deleted=0 AND EXISTS(SELECT 1 FROM functional_case c JOIN case_review_functional_case m ON CAST(c.id AS BINARY(50))=CAST(m.case_id AS BINARY(50)) WHERE c.id=h.case_id AND c.deleted=0 AND m.review_id=h.review_id)",reviewId);
        if(Set.of("COMPLETED","ARCHIVED").contains(review.get("status"))){
            var previous=new PlanReviewFrozenReader(jdbc).frame("review",reviewId);
            if(previous.get("data") instanceof Map<?,?> data){if(data.get("members")!=null)members=data.get("members");if(data.get("opinions")!=null)opinions=data.get("opinions");}
        }
        review.put("status",state);
        var snapshot=Map.of("review",review,"members",members,"opinions",opinions,"source",List.of(),"sourceStatistics",Map.of());
        jdbc.update("INSERT INTO plan_review_frozen_snapshot(id,project_id,object_side,object_id,relation_id,generation,object_state,source_version,applied_version,target_fingerprint,snapshot,actor,create_time) VALUES(?,?,'review',?,NULL,NULL,?,0,0,'independent',?,?,?)",
                java.util.UUID.randomUUID().toString(),review.get("project_id"),reviewId,state,io.metersphere.sdk.util.JSON.toJSONString(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8),actor,now());
    }

    public Map<String, Long> currentEpochs(String reviewId) {
        return jdbc.queryForList("SELECT case_id,epoch FROM plan_review_target_member WHERE review_id=? AND present=1", reviewId)
                .stream().collect(Collectors.toUnmodifiableMap(row -> (String) row.get("case_id"),
                        row -> ((Number) row.get("epoch")).longValue()));
    }

    public void freeze(Map<String,Object> relation, String side, String objectId, String state, String fingerprint, String actor) {
        freeze(relation,side,objectId,state,fingerprint,actor,Map.of());
    }
    public void freeze(Map<String,Object> relation, String side, String objectId, String state, String fingerprint, String actor,Map<String,Object> sourceStatistics) {
        var currentSource=jdbc.queryForList("SELECT case_id,epoch,present FROM plan_review_source_member WHERE plan_id=?",relation.get("plan_id"));
        Object scope=jdbc.queryForList("SELECT m.case_id,m.epoch,c.num case_num,c.name case_name FROM plan_review_source_member m LEFT JOIN functional_case c ON CAST(c.id AS BINARY(50))=CAST(m.case_id AS BINARY(50)) WHERE m.plan_id=? AND m.present=1",relation.get("plan_id"));
        Object members=jdbc.queryForList("SELECT m.*,c.num case_num,c.name case_name FROM case_review_functional_case m LEFT JOIN functional_case c ON CAST(c.id AS BINARY(50))=CAST(m.case_id AS BINARY(50)) AND c.project_id=? WHERE m.review_id=?",relation.get("project_id"),relation.get("review_id"));
        Object opinions=jdbc.queryForList("SELECT * FROM case_review_history WHERE review_id=? AND deleted=0",relation.get("review_id"));
        Object review=jdbc.queryForMap("SELECT * FROM case_review WHERE id=?",relation.get("review_id"));
        if("plan".equals(side)){
            String objectState=jdbc.queryForObject("SELECT status FROM test_plan WHERE id=?",String.class,objectId);
            if(Set.of("COMPLETED","ARCHIVED").contains(objectState)){
                var previous=new PlanReviewFrozenReader(jdbc).frame(side,objectId);
                if(previous.get("data") instanceof Map<?,?> data && data.get("source")!=null)scope=data.get("source");
            }
        }
        else if("review".equals(side)){
            String objectState=jdbc.queryForObject("SELECT status FROM case_review WHERE id=?",String.class,objectId);
            if(Set.of("COMPLETED","ARCHIVED").contains(objectState)){
                var previous=new PlanReviewFrozenReader(jdbc).frame(side,objectId);
                if(previous.get("data") instanceof Map<?,?> data){if(data.get("members")!=null)members=data.get("members");if(data.get("opinions")!=null)opinions=data.get("opinions");if(data.get("review")!=null)review=data.get("review");}
            }
        }
        var snapshot = Map.of("source",scope,"currentSource",currentSource,
                "sourceStatistics",sourceStatistics,
                "review",review,"members",members,"opinions",opinions);
        jdbc.update("INSERT INTO plan_review_frozen_snapshot(id,project_id,object_side,object_id,relation_id,generation,object_state,source_version,applied_version,target_fingerprint,snapshot,actor,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                java.util.UUID.randomUUID().toString(),relation.get("project_id"),side,objectId,relation.get("id"),relation.get("generation"),state,
                relation.get("source_version"),relation.get("applied_version"),fingerprint,io.metersphere.sdk.util.JSON.toJSONString(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8),actor,now());
    }

    private List<Map<String, Object>> active(String reviewId) {
        return jdbc.queryForList("SELECT * FROM plan_review_relation WHERE active_review_id=? AND state='ACTIVE' FOR UPDATE", reviewId);
    }

    public void lockReview(String reviewId) {
        var reviews = jdbc.queryForList("SELECT project_id FROM case_review WHERE id=?", String.class, reviewId);
        if (reviews.isEmpty()) throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE", 404, "评审不可访问");
        String project = reviews.getFirst();
        jdbc.update("INSERT IGNORE INTO plan_review_grant(project_id,update_time) VALUES(?,?)", project, now());
        jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE", project);
        var plans = jdbc.queryForList("SELECT plan_id FROM plan_review_relation WHERE active_review_id=? AND project_id=? FOR UPDATE", String.class, reviewId, project);
        for (String plan : plans) jdbc.queryForList("SELECT id FROM test_plan WHERE id=? FOR UPDATE", plan);
        jdbc.queryForList("SELECT id FROM case_review WHERE id=? AND project_id=? FOR UPDATE", reviewId, project);
        active(reviewId);
    }

    private static boolean truth(Object value) {
        return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() != 0
                || value instanceof byte[] bytes && bytes.length > 0 && bytes[0] != 0;
    }

    private static long now() { return System.currentTimeMillis(); }
}
