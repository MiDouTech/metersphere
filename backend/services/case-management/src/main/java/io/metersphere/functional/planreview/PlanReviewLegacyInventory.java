package io.metersphere.functional.planreview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Read-only migration manifest. It never chooses conflicts, pairs unlinked plans or starts workers. */
@Service
public class PlanReviewLegacyInventory {
    private final JdbcTemplate jdbc;
    private final PlanReviewAuthorization authorization;
    private final PlanReviewPlanStateResolver states;
    public PlanReviewLegacyInventory(JdbcTemplate jdbc,PlanReviewAuthorization authorization,PlanReviewPlanStateResolver states){this.jdbc=jdbc;this.authorization=authorization;this.states=states;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> scan(String project,String actor){
        authorization.requireRead(actor,project,true);authorization.requireRead(actor,project,false);
        var groups=jdbc.queryForList("SELECT id,project_id,plan_id,review_id,revision FROM functional_import_followup_group WHERE project_id=? ORDER BY id",project);
        var physical=new HashSet<String>();groups.forEach(group->physical.add((String)group.get("id")));
        var archived=new HashSet<String>();
        for(var archive:jdbc.queryForList("SELECT m.legacy_group_id,m.original_json,m.state FROM plan_review_legacy_group_map m JOIN plan_review_legacy_change c ON c.id=m.change_id AND c.project_id=m.project_id WHERE m.project_id=? ORDER BY c.create_time DESC,c.id DESC",project)){
            String id=(String)archive.get("legacy_group_id");if(physical.contains(id)||!archived.add(id))continue;
            if(Set.of("ISOLATED","ROLLED_BACK").contains(archive.get("state"))){var original=io.metersphere.sdk.util.JSON.parseObject((String)archive.get("original_json"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});if(project.equals(original.get("project_id")))groups.add(original);}
        }
        groups.sort(Comparator.comparing(group->(String)group.get("id")));
        var rows=new ArrayList<Map<String,Object>>();
        var targets=new HashMap<String,Set<String>>();var sources=new HashMap<String,Set<String>>();var duplicates=new HashMap<String,Integer>();
        for(var group:groups){String plan=(String)group.get("plan_id"),review=(String)group.get("review_id");targets.computeIfAbsent(plan,key->new HashSet<>()).add(review);sources.computeIfAbsent(review,key->new HashSet<>()).add(plan);duplicates.merge(plan+":"+review,1,Integer::sum);}
        for(var group:groups){
            String plan=(String)group.get("plan_id"),review=(String)group.get("review_id");
            var plans=jdbc.queryForList("SELECT id,project_id,type,status FROM test_plan WHERE id=?",plan).stream().map(states::resolve).toList();
            var reviews=jdbc.queryForList("SELECT id,project_id,status FROM case_review WHERE id=?",review);
            boolean ownPlan=plans.size()==1&&project.equals(plans.getFirst().get("project_id")),ownReview=reviews.size()==1&&project.equals(reviews.getFirst().get("project_id"));
            String classification;
            if(plans.isEmpty()||reviews.isEmpty())classification="ORPHAN";
            else if(!ownPlan||!ownReview)classification="CROSS_PROJECT";
            else if(!"TEST_PLAN".equals(plans.getFirst().get("type")))classification="CONTRADICTORY_FIELDS";
            else if(targets.get(plan).size()>1||sources.get(review).size()>1)classification="MULTIPLE_TARGETS";
            else if(!known((String)plans.getFirst().get("status"))||!known((String)reviews.getFirst().get("status")))classification="UNKNOWN_STATE";
            else if(!editable((String)plans.getFirst().get("status"))||!editable((String)reviews.getFirst().get("status")))classification="LOCKED";
            else classification=duplicates.get(plan+":"+review)>1?"DUPLICATE_PAIR":"LEGAL_UNIQUE";
            var row=new TreeMap<String,Object>();row.put("legacyGroupId",group.get("id"));row.put("legacyRevision",group.get("revision"));row.put("classification",classification);
            row.put("origin",physical.contains(group.get("id"))?"LEGACY_ACTIVE":"ISOLATED_ARCHIVE");
            row.put("planId",ownPlan?plan:"");row.put("reviewId",ownReview?review:"");
            if(ownPlan&&ownReview){
                var source=new TreeSet<>(jdbc.queryForList("SELECT DISTINCT m.functional_case_id FROM test_plan_functional_case m JOIN functional_case c ON CAST(c.id AS BINARY(50))=CAST(m.functional_case_id AS BINARY(50)) WHERE m.test_plan_id=? AND c.project_id=? AND c.deleted=0",String.class,plan,project));
                var target=new TreeSet<>(jdbc.queryForList("SELECT case_id FROM case_review_functional_case WHERE review_id=?",String.class,review));
                var adds=new TreeSet<>(source);adds.removeAll(target);var removes=new TreeSet<>(target);removes.removeAll(source);
                row.put("sourceCount",source.size());row.put("targetCount",target.size());row.put("addCount",adds.size());row.put("removeCount",removes.size());
                row.put("historyReferenceCount",jdbc.queryForObject("SELECT COUNT(*) FROM case_review_history WHERE review_id=?",Long.class,review));
                // Hash member identities instead of exporting case content or opinion bodies.
                row.put("sourceHash",hash(source));row.put("targetHash",hash(target));
            }
            row.put("jobs",jdbc.queryForList("SELECT job_id,policy,state,plan_id,review_id FROM functional_import_followup WHERE project_id=? AND group_id=? ORDER BY job_id",project,group.get("id")).stream().map(job->{
                var safe=new TreeMap<String,Object>();safe.put("jobId",job.get("job_id"));safe.put("policy",job.get("policy"));safe.put("state",job.get("state"));
                boolean planMatches=Objects.equals(plan,job.get("plan_id")),reviewMatches=Objects.equals(review,job.get("review_id"));
                safe.put("stage",job.get("plan_id")==null?"NO_PLAN":job.get("review_id")==null?"PLAN_ONLY":!planMatches||!reviewMatches?"CONTRADICTORY_IDS":"FIXED_PAIR");
                safe.put("successfulPlanItems",jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup_item WHERE job_id=? AND plan_done=1",Long.class,job.get("job_id")));
                safe.put("watermarkHash",watermarkHash((String)job.get("job_id")));
                safe.put("successfulReviewItems",jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup_item WHERE job_id=? AND review_done=1",Long.class,job.get("job_id")));return safe;
            }).toList());
            rows.add(row);
        }
        var result=new TreeMap<String,Object>();result.put("projectId",project);result.put("previewVersion",1);result.put("groups",rows);
        result.put("unlinkedPlanCount",jdbc.queryForObject("SELECT COUNT(*) FROM test_plan p WHERE p.project_id=? AND p.type='TEST_PLAN' AND NOT EXISTS(SELECT 1 FROM functional_import_followup_group g WHERE CAST(g.project_id AS BINARY(50))=CAST(p.project_id AS BINARY(50)) AND CAST(g.plan_id AS BINARY(50))=CAST(p.id AS BINARY(50))) AND NOT EXISTS(SELECT 1 FROM plan_review_relation r WHERE CAST(r.project_id AS BINARY(50))=CAST(p.project_id AS BINARY(50)) AND CAST(r.active_plan_id AS BINARY(50))=CAST(p.id AS BINARY(50)))",Long.class,project));
        result.put("unlinkedPlans",jdbc.queryForList("SELECT p.id,p.status FROM test_plan p WHERE p.project_id=? AND p.type='TEST_PLAN' AND NOT EXISTS(SELECT 1 FROM functional_import_followup_group g WHERE CAST(g.project_id AS BINARY(50))=CAST(p.project_id AS BINARY(50)) AND CAST(g.plan_id AS BINARY(50))=CAST(p.id AS BINARY(50))) AND NOT EXISTS(SELECT 1 FROM plan_review_relation r WHERE CAST(r.project_id AS BINARY(50))=CAST(p.project_id AS BINARY(50)) AND CAST(r.active_plan_id AS BINARY(50))=CAST(p.id AS BINARY(50))) ORDER BY p.id",project));
        var orphanJobs=jdbc.queryForList("SELECT job_id,policy,state FROM functional_import_followup f WHERE f.project_id=? AND NOT EXISTS(SELECT 1 FROM functional_import_followup_group g WHERE CAST(g.id AS BINARY(50))=CAST(f.group_id AS BINARY(50)) AND CAST(g.project_id AS BINARY(50))=CAST(f.project_id AS BINARY(50))) ORDER BY job_id",project);
        orphanJobs.forEach(job->job.put("watermarkHash",watermarkHash((String)job.get("job_id"))));result.put("orphanJobs",orphanJobs);
        result.put("manifestHash",hash(result));result.put("executionAllowed",false);
        result.put("requiredBeforeExecution",List.of("PROJECT_OWNER_APPROVAL_FOR_EXACT_MANIFEST","VERIFIED_BACKUP_AND_ISOLATED_REHEARSAL","OLD_WORKER_QUIESCENCE_AND_IN_FLIGHT_FENCE","CURRENT_DUAL_READ_UPDATE_AND_SERVICE_GRANT"));
        return result;
    }
    private String watermarkHash(String job){return hash(jdbc.queryForList("SELECT case_id,plan_done,review_done FROM functional_import_followup_item WHERE job_id=? ORDER BY case_id",job));}
    static String hash(Object value){
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(io.metersphere.sdk.util.JSON.toJSONString(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static boolean known(String status){return status!=null&&Set.of("PREPARED","UNDERWAY","COMPLETED","ARCHIVED").contains(status);}
    private static boolean editable(String status){return Set.of("PREPARED","UNDERWAY").contains(status);}
}
