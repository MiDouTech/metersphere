package io.metersphere.functional.planreview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Bounded project snapshot. Opinion bodies and credential values never leave their original tables. */
@Component
public class PlanReviewLegacySnapshot {
    private final JdbcTemplate jdbc;
    private final PlanReviewPlanStateResolver states;
    public PlanReviewLegacySnapshot(JdbcTemplate jdbc,PlanReviewPlanStateResolver states){this.jdbc=jdbc;this.states=states;}
    public Map<String,Object> read(PlanReviewLegacyDecision selection,boolean lock){
        return read(selection,lock,false);
    }
    public Map<String,Object> read(PlanReviewLegacyDecision selection,boolean lock,boolean includeApplied){
        String project=selection.projectId(),suffix=lock?" FOR UPDATE":"";
        var groups=new ArrayList<Map<String,Object>>();var physicalGroups=new TreeSet<String>();
        for(String id:selection.groups().keySet()){
            var rows=jdbc.queryForList("SELECT * FROM functional_import_followup_group WHERE id=? AND project_id=?"+suffix,id,project);
            if(!rows.isEmpty())physicalGroups.add(id);
            if(rows.isEmpty()){
                var archives=jdbc.queryForList("SELECT m.original_json,m.state FROM plan_review_legacy_group_map m JOIN plan_review_legacy_change c ON c.id=m.change_id AND c.project_id=m.project_id WHERE m.project_id=? AND m.legacy_group_id=? ORDER BY c.create_time DESC,c.id DESC LIMIT 1"+suffix,project,id);
                if(archives.size()!=1||!(includeApplied?Set.of("APPLIED","ISOLATED","RELEASED","ROLLED_BACK"):Set.of("ISOLATED","ROLLED_BACK")).contains(archives.getFirst().get("state")))throw unavailable();
                rows=List.of(io.metersphere.sdk.util.JSON.parseObject((String)archives.getFirst().get("original_json"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));
            }
            if(rows.size()!=1||!project.equals(rows.getFirst().get("project_id")))throw unavailable();groups.add(new TreeMap<>(rows.getFirst()));
        }
        var plans=new TreeSet<String>();var reviews=new TreeSet<String>();
        groups.forEach(group->{add(plans,group.get("plan_id"));add(reviews,group.get("review_id"));});
        var jobs=new ArrayList<Map<String,Object>>();
        for(String id:selection.jobs().keySet()){
            var rows=jdbc.queryForList("SELECT job_id,project_id,user_id,policy,request_json,group_id,plan_id,review_id,state,create_time,update_time,source_kind FROM functional_import_followup WHERE job_id=? AND project_id=?"+suffix,id,project);
            if(rows.size()!=1||!project.equals(rows.getFirst().get("project_id")))throw unavailable();
            var row=new TreeMap<>(rows.getFirst());
            if("IMPORT_ONLY".equals(row.get("policy")))throw new PlanReviewFailure("MIGRATION_SCOPE_INVALID",409,"仅导入任务不纳入关系迁移，保持原完成路径");
            jobs.add(row);add(plans,row.get("plan_id"));add(reviews,row.get("review_id"));
        }
        // Every old worker capable of writing these objects must be included in the reviewed scope.
        var relatedJobs=new TreeSet<String>();var relatedGroups=new TreeSet<String>();
        var protectedPlans=new TreeSet<String>();var protectedReviews=new TreeSet<String>();
        for(var group:groups)if("KEEP".equals(selection.groups().get(group.get("id")))){add(protectedPlans,group.get("plan_id"));add(protectedReviews,group.get("review_id"));}
        for(var job:jobs)if("ADOPT".equals(selection.jobs().get(job.get("job_id")).action())){add(protectedPlans,job.get("plan_id"));add(protectedReviews,job.get("review_id"));}
        for(String plan:protectedPlans){
            relatedJobs.addAll(jdbc.queryForList("SELECT job_id FROM functional_import_followup WHERE project_id=? AND policy<>'IMPORT_ONLY' AND plan_id=?"+suffix,String.class,project,plan));
            relatedGroups.addAll(jdbc.queryForList("SELECT id FROM functional_import_followup_group WHERE project_id=? AND plan_id=?"+suffix,String.class,project,plan));
            if(jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup_group WHERE project_id<>? AND plan_id=?",Integer.class,project,plan)>0)throw crossDependency();
        }
        for(String review:protectedReviews){
            relatedJobs.addAll(jdbc.queryForList("SELECT job_id FROM functional_import_followup WHERE project_id=? AND policy<>'IMPORT_ONLY' AND review_id=?"+suffix,String.class,project,review));
            relatedGroups.addAll(jdbc.queryForList("SELECT id FROM functional_import_followup_group WHERE project_id=? AND review_id=?"+suffix,String.class,project,review));
            if(jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup_group WHERE project_id<>? AND review_id=?",Integer.class,project,review)>0)throw crossDependency();
        }
        for(String group:selection.groups().keySet())relatedJobs.addAll(jdbc.queryForList("SELECT job_id FROM functional_import_followup WHERE project_id=? AND policy<>'IMPORT_ONLY' AND group_id=?"+suffix,String.class,project,group));
        for(var job:jobs)if("ADOPT".equals(selection.jobs().get(job.get("job_id")).action())&&job.get("group_id")!=null)relatedJobs.addAll(jdbc.queryForList("SELECT job_id FROM functional_import_followup WHERE project_id=? AND policy<>'IMPORT_ONLY' AND group_id=?"+suffix,String.class,project,job.get("group_id")));
        if(!selection.jobs().keySet().containsAll(relatedJobs)||!selection.groups().keySet().containsAll(relatedGroups))throw new PlanReviewFailure("MIGRATION_SCOPE_INCOMPLETE",409,"清单未包含全部关联旧任务或冲突边，请补齐选择后重新预览");
        var result=new TreeMap<String,Object>();result.put("projectId",project);result.put("groups",groups);result.put("physicalGroups",physicalGroups);result.put("jobs",jobs);
        result.put("projectGrant",jdbc.queryForList("SELECT project_id,revision,enabled FROM plan_review_grant WHERE project_id=?"+suffix,project));
        var unified=new TreeMap<String,Map<String,Object>>();
        for(String plan:plans)for(var relation:jdbc.queryForList("SELECT * FROM plan_review_relation WHERE project_id=? AND plan_id=? ORDER BY id"+suffix,project,plan))unified.put((String)relation.get("id"),new TreeMap<>(relation));
        for(String review:reviews)for(var relation:jdbc.queryForList("SELECT * FROM plan_review_relation WHERE project_id=? AND review_id=? ORDER BY id"+suffix,project,review))unified.put((String)relation.get("id"),new TreeMap<>(relation));
        result.put("unifiedRelations",List.copyOf(unified.values()));
        var planRows=new ArrayList<Map<String,Object>>();var reviewRows=new ArrayList<Map<String,Object>>();
        var sourceMembers=new ArrayList<Map<String,Object>>();var reviewMembers=new ArrayList<Map<String,Object>>();var opinions=new ArrayList<Map<String,Object>>();var reviewers=new ArrayList<Map<String,Object>>();
        for(String id:plans){
            var rows=jdbc.queryForList("SELECT id,project_id,type,status FROM test_plan WHERE id=?"+suffix,id);
            if(!rows.isEmpty()&&!project.equals(rows.getFirst().get("project_id")))continue;
            if(!rows.isEmpty())planRows.add(new TreeMap<>(states.resolve(rows.getFirst())));
            sourceMembers.addAll(jdbc.queryForList("SELECT m.id,m.test_plan_id,m.functional_case_id,c.deleted,c.project_id case_project_id FROM test_plan_functional_case m JOIN test_plan p ON CAST(p.id AS BINARY(50))=CAST(m.test_plan_id AS BINARY(50)) LEFT JOIN functional_case c ON CAST(c.id AS BINARY(50))=CAST(m.functional_case_id AS BINARY(50)) WHERE p.id=? AND p.project_id=? ORDER BY m.id"+suffix,id,project));
        }
        String digest=jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)connection->connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")?"SHA2(content,256)":"LOWER(RAWTOHEX(HASH('SHA-256',content)))");
        for(String id:reviews){
            var rows=jdbc.queryForList("SELECT id,project_id,status FROM case_review WHERE id=?"+suffix,id);
            if(rows.isEmpty()||!project.equals(rows.getFirst().get("project_id")))continue;
            reviewRows.add(new TreeMap<>(rows.getFirst()));
            reviewers.addAll(jdbc.queryForList("SELECT review_id,user_id FROM case_review_user WHERE review_id=? ORDER BY user_id"+suffix,id));
            reviewMembers.addAll(jdbc.queryForList("SELECT id,review_id,case_id,status,create_time,create_user,update_time,pos FROM case_review_functional_case WHERE review_id=? ORDER BY id"+suffix,id));
            opinions.addAll(jdbc.queryForList("SELECT id,review_id,case_id,status,"+digest+" content_hash,deleted,abandoned FROM case_review_history WHERE review_id=? ORDER BY id"+suffix,id));
        }
        var watermarks=new ArrayList<Map<String,Object>>();
        for(String job:selection.jobs().keySet())watermarks.addAll(jdbc.queryForList("SELECT job_id,case_id,plan_done,review_done FROM functional_import_followup_item WHERE job_id=? ORDER BY case_id"+suffix,job));
        result.put("plans",planRows);result.put("reviews",reviewRows);result.put("reviewers",reviewers);result.put("sourceMembers",sourceMembers);result.put("reviewMembers",reviewMembers);result.put("opinionReferences",opinions);result.put("watermarks",watermarks);
        return result;
    }
    public static String hash(Map<String,Object> snapshot){return PlanReviewLegacyInventory.hash(snapshot);}
    private static void add(Set<String> ids,Object id){if(id instanceof String value&&!value.isBlank())ids.add(value);}
    private static PlanReviewFailure unavailable(){return new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"原历史记录不可访问或已完成移交，不会新选其他目标");}
    private static PlanReviewFailure crossDependency(){return new PlanReviewFailure("CROSS_PROJECT_DEPENDENCY",409,"存在其他项目的旧引用，需其有权者先处理；当前项目不会跨项目写入");}
}
