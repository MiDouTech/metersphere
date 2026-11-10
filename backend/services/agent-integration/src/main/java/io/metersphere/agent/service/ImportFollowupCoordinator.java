package io.metersphere.agent.service;

import io.metersphere.agent.dto.ImportFollowupRequest;
import io.metersphere.functional.request.BaseAssociateCaseRequest;
import io.metersphere.functional.request.CaseReviewAssociateRequest;
import io.metersphere.functional.request.CaseReviewRequest;
import io.metersphere.functional.service.CaseReviewService;
import io.metersphere.plan.dto.request.TestPlanCreateRequest;
import io.metersphere.plan.dto.request.TestPlanFunctionalCaseAssociateRequest;
import io.metersphere.plan.service.TestPlanFunctionalCaseService;
import io.metersphere.plan.service.TestPlanService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.service.UserLoginService;
import io.metersphere.system.dto.sdk.SessionUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Durable web-import follow-up. Existing import item transactions remain unchanged. */
@Service
public class ImportFollowupCoordinator {
    @jakarta.annotation.Resource private io.metersphere.functional.planreview.PlanReviewAuthorization relationAuthorization;
    @jakarta.annotation.Resource private io.metersphere.functional.planreview.PlanReviewApplicationService relations;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AgentCaseImportService imports;
    private final AgentBusinessAccess access;
    private final TestPlanService plans;
    private final TestPlanFunctionalCaseService planCases;
    private final CaseReviewService reviews;
    private final UserLoginService users;

    public ImportFollowupCoordinator(JdbcTemplate jdbc, PlatformTransactionManager manager, AgentCaseImportService imports,
            AgentBusinessAccess access, TestPlanService plans, TestPlanFunctionalCaseService planCases,
            CaseReviewService reviews, UserLoginService users) {
        this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); tx.setTimeout(25);
        this.imports=imports; this.access=access; this.plans=plans; this.planCases=planCases; this.reviews=reviews; this.users=users;
    }

    private Map<String,Object> owned(String id,String user) { return owned(id,user,"WEB"); }
    private Map<String,Object> owned(String id,String user,String token) {
        var file=jdbc.queryForList("SELECT project_id,source_name,state,token_id,'FILE' source_kind FROM functional_import_job WHERE id=? AND user_id=? AND token_id=?",id,user,token);
        if(!file.isEmpty()){imports.get(id,user,token,1,1);return file.getFirst();}
        if(!"WEB".equals(token))throw AgentBusinessAccess.denied();
        var asset=jdbc.queryForList("SELECT scope_project_id project_id,'用例资产导入' source_name,status state,'WEB' token_id,'ASSET' source_kind FROM default_hub_sync_job WHERE id=? AND create_user=? AND job_type='IMPORT_CASE'",id,user);
        if(asset.size()!=1)throw AgentBusinessAccess.denied();
        String project=(String)asset.getFirst().get("project_id");
        requireProjectImport(project,user);
        return asset.getFirst();
    }
    public Map<String,Object> optionsForProject(String project,String user) {
        requireProjectImport(project,user);
        return projectOptions(project,user);
    }

    public Map<String,Object> options(String id,String user) { return options(id,user,"WEB"); }
    public Map<String,Object> options(String id,String user,String token) {
        var job=owned(id,user,token);
        String project=(String)job.get("project_id");
        return projectOptions(project,user);
    }
    private void requireProjectImport(String project,String user) {
        Long active=jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id=? AND deleted=0 AND enable=1",Long.class,project);
        if(active==null||active!=1||!access.userHasPermission(user,project,PermissionConstants.FUNCTIONAL_CASE_READ_IMPORT))throw AgentBusinessAccess.denied();
    }
    private Map<String,Object> projectOptions(String project,String user) {
        var groups=jdbc.queryForList("SELECT g.id,g.source_version revision,g.generation,g.plan_id planId,g.review_id reviewId,p.name planName,r.name reviewName "
                + "FROM plan_review_relation g JOIN test_plan p ON BINARY p.id=BINARY g.active_plan_id JOIN case_review r ON BINARY r.id=BINARY g.active_review_id "
                + "WHERE g.project_id=? AND g.state='ACTIVE' AND p.status<>'ARCHIVED' AND r.status IN ('PREPARED','UNDERWAY') ORDER BY g.create_time DESC,g.id LIMIT 100",project)
                .stream().filter(group -> {
                    try { return Boolean.TRUE.equals(((Map<?, ?>) relations.summary("plan", (String)group.get("planId"),user).get("sync")).get("editable")); }
                    catch (io.metersphere.functional.planreview.PlanReviewFailure denied) { return false; }
                }).toList();
        var current=relationAuthorization.current(user,project,true);
        boolean allowed=io.metersphere.functional.planreview.PlanReviewPolicy.allowedCreatePair(current,1);
        boolean permitted=io.metersphere.functional.planreview.PlanReviewPolicy.allowedCreatePair(
                new io.metersphere.functional.planreview.PlanReviewPolicy.Access(current.permissions(),true),1);
        return Map.of("projectId",project,"groups",(access.userHasPermission(user,project,PermissionConstants.TEST_PLAN_READ) && access.userHasPermission(user,project,PermissionConstants.CASE_REVIEW_READ)) ? groups : List.of(),"canCreate",allowed,
                "canCreatePermission",permitted,"serviceGranted",true);
    }

    public void prepare(String id,String user,ImportFollowupRequest request) { prepare(id,user,request,"WEB"); }
    public void prepare(String id,String user,ImportFollowupRequest request,String token) {
        if (request==null) return; // Legacy callers keep their established import-only contract.
        owned(id,user,token);
        if(!Set.of("CREATE_NEW_PAIR","APPEND_EXISTING","IMPORT_ONLY").contains(Objects.toString(request.getPolicy(),"")))throw new MSException("导入关联策略无效");
        tx.executeWithoutResult(status->{
            jdbc.queryForList("SELECT id FROM "+("ASSET".equals(owned(id,user,token).get("source_kind"))?"default_hub_sync_job":"functional_import_job")+" WHERE id=? FOR UPDATE",id);
            var job=owned(id,user,token);
            String payload=JSON.toJSONString(request);
            var existing=jdbc.queryForList("SELECT request_json FROM plan_review_import_followup WHERE job_id=?",id);
            if(!existing.isEmpty()) {
                if(!payload.equals(existing.getFirst().get("request_json")))throw new MSException("导入关联选择已提交，不能变更；请刷新查看结果");
                return;
            }
            if(!"ASSET".equals(job.get("source_kind"))&&!"PRECHECKED".equals(job.get("state")))throw new MSException("请在导入提交前选择关联策略");
            String project=(String)job.get("project_id"),group=UUID.randomUUID().toString();
            String plan=null,review=null;
            if(!"IMPORT_ONLY".equals(request.getPolicy())) {
                checkTokenPolicy(job,user);
                permissions(project,user,request.getReviewers(),request.getPolicy());
                if("CREATE_NEW_PAIR".equals(request.getPolicy()) && (request.getReviewPassRule()==null||!Set.of("SINGLE","MULTIPLE").contains(request.getReviewPassRule())))throw new MSException("请选择评审通过规则");
            }
            if("APPEND_EXISTING".equals(request.getPolicy())) {
                jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE",project);
                permissions(project,user,request.getReviewers(),request.getPolicy());
                var groups=jdbc.queryForList("SELECT * FROM plan_review_relation WHERE id=? AND project_id=? AND state='ACTIVE' FOR UPDATE",request.getTargetGroupId(),project);
                if(groups.size()!=1)throw new MSException("所选导入关联组已失效");
                var g=groups.getFirst();
                if(request.getExpectedGroupRevision()==null || ((Number)g.get("source_version")).longValue()!=request.getExpectedGroupRevision())throw new MSException("关联组已更新，请刷新后重新选择");
                group=(String)g.get("id"); plan=(String)g.get("plan_id");review=(String)g.get("review_id");
                validateTargets(project,plan,review);
                var currentReviewers = targetReviewers(project,review);
                if(request.getReviewers()!=null && !request.getReviewers().isEmpty() && !new HashSet<>(request.getReviewers()).equals(new HashSet<>(currentReviewers)))
                    throw new MSException("AGENT_VALIDATION_ERROR");
            }
            long now=System.currentTimeMillis();
            jdbc.update("INSERT INTO plan_review_import_followup(job_id,project_id,user_id,policy,request_json,group_id,plan_id,review_id,state,create_time,update_time,source_kind) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    id,project,user,request.getPolicy(),payload,group,plan,review,"IMPORT_ONLY".equals(request.getPolicy())?"SKIPPED":"PENDING",now,now,job.get("source_kind"));
            if (plan != null) jdbc.update("UPDATE plan_review_import_followup SET relation_id=?,generation=(SELECT generation FROM plan_review_relation WHERE id=?) WHERE job_id=?",group,group,id);
            if(!"IMPORT_ONLY".equals(request.getPolicy())){
                String operation=UUID.randomUUID().toString();
                String generation=plan==null?null:jdbc.queryForObject("SELECT generation FROM plan_review_relation WHERE id=?",String.class,group);
                String credential="WEB".equals(job.get("token_id"))?null:(String)job.get("token_id");
                jdbc.update("INSERT INTO plan_review_operation(id,project_id,source_kind,operation_type,request_key,payload_hash,request_json,original_actor,executing_actor,executing_credential_id,initial_member_count,plan_id,review_id,relation_id,generation,stage,state,create_time,update_time) VALUES(?,?,'IMPORT_FOLLOWUP',?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?)",
                        operation,project,"CREATE_NEW_PAIR".equals(request.getPolicy())?"CREATE_PAIR":"APPEND",id,payloadHash(payload),payload,user,user,credential,"CREATE_NEW_PAIR".equals(request.getPolicy())?1:0,plan,review,plan==null?null:group,generation,"WAIT_IMPORT",now,now);
                jdbc.update("UPDATE plan_review_operation SET original_credential_id=executing_credential_id WHERE id=?",operation);
                jdbc.update("UPDATE plan_review_import_followup SET operation_id=? WHERE job_id=?",operation,id);
            }
        });
    }

    private void permissions(String project,String user,List<String> reviewers,String policy) {
        var current = relationAuthorization.current(user,project,true);
        if(!current.permissions().contains(io.metersphere.functional.planreview.PlanReviewPolicy.Permission.IMPORT))throw AgentBusinessAccess.denied();
        if("IMPORT_ONLY".equals(policy))return;
        if(!Set.of("CREATE_NEW_PAIR","APPEND_EXISTING").contains(Objects.toString(policy,"")))throw new MSException("导入关联策略无效");
        if ("CREATE_NEW_PAIR".equals(policy) ? !io.metersphere.functional.planreview.PlanReviewPolicy.allowedCreatePair(current,1)
                : !io.metersphere.functional.planreview.PlanReviewPolicy.allowed(io.metersphere.functional.planreview.PlanReviewPolicy.Action.APPEND,current)) {
            throw new MSException("当前账号缺少关联操作权限，可选择仅导入");
        }
        if("CREATE_NEW_PAIR".equals(policy)) access.reviewers(project,reviewers);
    }

    private List<String> targetReviewers(String project,String review) {
        var reviewers = jdbc.queryForList("SELECT u.user_id FROM case_review_user u JOIN case_review r ON BINARY r.id=BINARY u.review_id WHERE r.id=? AND r.project_id=? ORDER BY u.user_id",String.class,review,project);
        access.reviewers(project,reviewers);
        return reviewers;
    }

    private void validateTargets(String project,String plan,String review) {
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation g JOIN test_plan p ON BINARY p.id=BINARY g.active_plan_id JOIN case_review r ON BINARY r.id=BINARY g.active_review_id WHERE g.plan_id=? AND g.review_id=? AND g.project_id=? AND g.state='ACTIVE' AND r.status IN ('PREPARED','UNDERWAY')",Long.class,plan,review,project);
        if(count==null||count!=1)throw new MSException("目标计划或评审已删除、归档或结束，请重新选择关联目标");
        relations.requireSourceEditable(plan);
    }

    public Map<String,Object> status(String id,String user) { return status(id,user,"WEB"); }
    public Map<String,Object> status(String id,String user,String token) {
        var job=owned(id,user,token);
        var rows=jdbc.queryForList("SELECT f.state,f.policy,o.stage,f.error_message message,f.plan_id planId,f.review_id reviewId,p.name planName,r.name reviewName "
                + "FROM plan_review_import_followup f LEFT JOIN plan_review_operation o ON BINARY o.id=BINARY f.operation_id AND BINARY o.project_id=BINARY f.project_id "
                + "LEFT JOIN test_plan p ON BINARY p.id=BINARY f.plan_id LEFT JOIN case_review r ON BINARY r.id=BINARY f.review_id WHERE f.job_id=?",id);
        if(rows.isEmpty()){
            var legacy=jdbc.queryForList("SELECT policy,state FROM functional_import_followup WHERE job_id=? AND project_id=? AND user_id=?",id,job.get("project_id"),user);
            if(legacy.isEmpty())return Map.of("state","LEGACY");
            if("IMPORT_ONLY".equals(legacy.getFirst().get("policy")))return Map.of("state",legacy.getFirst().get("state"),"policy","IMPORT_ONLY","legacy",true);
            return Map.of("state","LEGACY_ADOPTION_REQUIRED","message","原关联后处理任务需受控迁移核对；原成功结果保留，不会另建对象","legacy",true);
        }
        var result=new LinkedHashMap<>(rows.getFirst());String project=(String)job.get("project_id");
        if(!access.userHasPermission(user,project,PermissionConstants.TEST_PLAN_READ)){result.remove("planId");result.remove("planName");}
        if(!access.userHasPermission(user,project,PermissionConstants.CASE_REVIEW_READ)){result.remove("reviewId");result.remove("reviewName");}
        return result;
    }

    public void retry(String id,String user) { retry(id,user,"WEB"); }
    public void retry(String id,String user,String token) {
        var job=owned(id,user,token);
        tx.executeWithoutResult(status->{
            String project=(String)job.get("project_id");relationAuthorization.current(user,project,true);
            var rows=jdbc.queryForList("SELECT * FROM plan_review_import_followup WHERE job_id=? AND user_id=? AND project_id=? FOR UPDATE",id,user,project);
            if(rows.isEmpty()){
                var legacy=jdbc.queryForList("SELECT policy,state,plan_id,review_id FROM functional_import_followup WHERE job_id=? AND user_id=? AND project_id=? FOR UPDATE",id,user,project);
                if(legacy.size()!=1)throw AgentBusinessAccess.denied();var old=legacy.getFirst();
                if(!"IMPORT_ONLY".equals(old.get("policy"))||old.get("plan_id")!=null||old.get("review_id")!=null)throw new io.metersphere.functional.planreview.PlanReviewFailure("LEGACY_ADOPTION_REQUIRED",409,"原关联任务须先完成受控移交，不会按新任务重新执行");
                if(!"FAILED".equals(old.get("state")))throw new io.metersphere.functional.planreview.PlanReviewFailure("OPERATION_NOT_FAILED",409,"只有失败的仅导入后处理可恢复");
                permissions(project,user,List.of(),"IMPORT_ONLY");runWithTokenPolicy(job,user,true,()->{});
                jdbc.update("UPDATE functional_import_followup SET state='PENDING',error_message=NULL,update_time=? WHERE job_id=? AND project_id=? AND policy='IMPORT_ONLY' AND state='FAILED'",System.currentTimeMillis(),id,project);return;
            }
            if(rows.size()!=1)throw AgentBusinessAccess.denied();var row=rows.getFirst();
            var operation=root(row,true);if(!user.equals(operation.get("executing_actor"))||"TERMINATED".equals(operation.get("state")))throw AgentBusinessAccess.denied();
            if(!"FAILED".equals(row.get("state")))throw new io.metersphere.functional.planreview.PlanReviewFailure("OPERATION_NOT_FAILED",409,"仅失败的后处理步骤可以恢复，已完成结果不会重开");
            var request=JSON.parseObject((String)row.get("request_json"),ImportFollowupRequest.class);
            permissions(project,user,request.getReviewers(),request.getPolicy());checkTokenPolicy(job,user);
            if(row.get("relation_id")!=null && jdbc.queryForList("SELECT id FROM plan_review_relation WHERE id=? AND generation=? AND project_id=? AND state='ACTIVE' FOR UPDATE",row.get("relation_id"),row.get("generation"),project).size()!=1)throw new io.metersphere.functional.planreview.PlanReviewFailure("RELATION_CHANGED",409,"原关联已结束，不能重放旧任务");
            jdbc.update("UPDATE plan_review_import_followup SET state='PENDING',error_message=NULL,update_time=? WHERE job_id=? AND user_id=? AND state='FAILED'",System.currentTimeMillis(),id,user);
            jdbc.update("UPDATE plan_review_operation SET state='PENDING',revision=revision+1,update_time=? WHERE id=?",System.currentTimeMillis(),operation.get("id"));
        });
    }

    @Scheduled(fixedDelay=3000,initialDelay=30000,scheduler="functionalImportEffectsScheduler")
    public void drain() {
        drainLegacyImportOnly();
        var due=jdbc.queryForList("SELECT f.job_id FROM plan_review_import_followup f LEFT JOIN functional_import_job j ON BINARY j.id=BINARY f.job_id AND f.source_kind='FILE' LEFT JOIN default_hub_sync_job a ON BINARY a.id=BINARY f.job_id AND f.source_kind='ASSET' WHERE f.state='PENDING' AND (j.state IN ('COMPLETED','PARTIAL','STOPPED') OR a.status IN ('SUCCESS','PARTIAL_SUCCESS','FAILED')) ORDER BY f.update_time,f.job_id LIMIT 1",String.class);
        if(due.isEmpty())return;
        String id=due.getFirst();
        var observedRows=jdbc.queryForList("SELECT p.* FROM plan_review_operation p JOIN plan_review_import_followup f ON f.operation_id=p.id WHERE f.job_id=?",id);
        var observed=observedRows.isEmpty()?null:observedRows.getFirst();
        try { prepareImportReservation(id); tx.executeWithoutResult(status->process(id)); }
        catch(Exception failure) {
            io.metersphere.functional.planreview.PlanReviewSyncDiagnostics.importFailure(observed==null?null:Objects.toString(observed.get("project_id"),null),observed==null?null:Objects.toString(observed.get("id"),null),id,failure);
            if(observed==null){jdbc.update("UPDATE plan_review_import_followup SET state='FAILED',error_message='Original operation unavailable' WHERE job_id=? AND operation_id IS NULL AND state='PENDING'",id);return;}
            tx.executeWithoutResult(status->{
                jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE",observed.get("project_id"));
                var current=jdbc.queryForList("SELECT * FROM plan_review_operation WHERE id=? AND project_id=? FOR UPDATE",observed.get("id"),observed.get("project_id"));
                if(current.size()!=1)return;var operation=current.getFirst();
                if(!Objects.equals(operation.get("revision"),observed.get("revision"))||!Objects.equals(operation.get("executing_actor"),observed.get("executing_actor"))||Set.of("TERMINATED","COMPLETED").contains(operation.get("state")))return;
                String reason=failure instanceof io.metersphere.functional.planreview.PlanReviewFailure denied?denied.getMessage():"Import followup paused; committed results are retained";
                jdbc.update("UPDATE plan_review_import_followup SET state='FAILED',error_message=?,update_time=? WHERE job_id=? AND operation_id=? AND state='PENDING'",reason,System.currentTimeMillis(),id,operation.get("id"));
                jdbc.update("UPDATE plan_review_operation SET state='FAILED',safe_error=?,revision=revision+1,update_time=? WHERE id=?",reason,System.currentTimeMillis(),operation.get("id"));
            });
        }
    }

    /** Completing old IMPORT_ONLY bookkeeping never adopts a relationship or replays business writes. */
    private void drainLegacyImportOnly(){
        var due=jdbc.queryForList("SELECT f.job_id,f.project_id FROM functional_import_followup f LEFT JOIN functional_import_job j ON BINARY j.id=BINARY f.job_id AND BINARY j.project_id=BINARY f.project_id AND f.source_kind='FILE' LEFT JOIN default_hub_sync_job a ON BINARY a.id=BINARY f.job_id AND BINARY a.scope_project_id=BINARY f.project_id AND f.source_kind='ASSET' WHERE f.policy='IMPORT_ONLY' AND f.state='PENDING' AND f.plan_id IS NULL AND f.review_id IS NULL AND (j.state IN ('COMPLETED','PARTIAL','STOPPED') OR a.status IN ('SUCCESS','PARTIAL_SUCCESS','FAILED')) ORDER BY f.update_time,f.job_id LIMIT 1");
        if(due.isEmpty())return;var selected=due.getFirst();
        tx.executeWithoutResult(status->{
            var rows=jdbc.queryForList("SELECT job_id FROM functional_import_followup WHERE job_id=? AND project_id=? AND policy='IMPORT_ONLY' AND state='PENDING' AND plan_id IS NULL AND review_id IS NULL FOR UPDATE SKIP LOCKED",selected.get("job_id"),selected.get("project_id"));
            if(rows.isEmpty())return;
            jdbc.update("UPDATE functional_import_followup SET state='SUCCEEDED',error_message=NULL,update_time=? WHERE job_id=? AND project_id=? AND policy='IMPORT_ONLY' AND state='PENDING'",System.currentTimeMillis(),selected.get("job_id"),selected.get("project_id"));
        });
    }

    private Map<String,Object> sourceSnapshot(Map<String,Object> row,Map<String,Object> operation){
        String id=(String)row.get("job_id"),project=(String)row.get("project_id"),original=(String)row.get("user_id");
        String query="ASSET".equals(row.get("source_kind"))
                ?"SELECT scope_project_id project_id,'Asset import' source_name,status state FROM default_hub_sync_job WHERE id=? AND create_user=? AND scope_project_id=? AND job_type='IMPORT_CASE'"
                :"SELECT project_id,source_name,state FROM functional_import_job WHERE id=? AND user_id=? AND project_id=?";
        var sources=jdbc.queryForList(query,id,original,project);
        if(sources.size()!=1)throw AgentBusinessAccess.denied();
        var source=new LinkedHashMap<>(sources.getFirst());
        source.put("token_id",operation.get("executing_credential_id")==null?"WEB":operation.get("executing_credential_id"));
        return source;
    }
    private Map<String,Object> root(Map<String,Object> row,boolean lock){
        var rows=jdbc.queryForList("SELECT * FROM plan_review_operation WHERE id=? AND project_id=? AND source_kind='IMPORT_FOLLOWUP'"+(lock?" FOR UPDATE":""),row.get("operation_id"),row.get("project_id"));
        if(rows.size()!=1)throw new io.metersphere.functional.planreview.PlanReviewFailure("PARENT_OPERATION_REQUIRED",409,"Original import operation is unavailable");
        return rows.getFirst();
    }
    private void progress(Map<String,Object> row,String stage,String state){
        jdbc.update("UPDATE plan_review_operation SET stage=?,state=?,safe_error=NULL,revision=revision+1,update_time=? WHERE id=? AND state<>'TERMINATED'",stage,state,System.currentTimeMillis(),row.get("operation_id"));
    }
    public void resumeOperation(Map<String,Object> operation,String actor){
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Import recovery requires its operation transaction");
        var rows=jdbc.queryForList("SELECT * FROM plan_review_import_followup WHERE operation_id=? AND project_id=? FOR UPDATE",operation.get("id"),operation.get("project_id"));
        if(rows.size()!=1)throw AgentBusinessAccess.denied();var row=rows.getFirst();
        if(Set.of("SUCCEEDED","SKIPPED","TERMINATED").contains(row.get("state")))throw new io.metersphere.functional.planreview.PlanReviewFailure("OPERATION_FINISHED",409,"Completed import followup cannot be reopened");
        var request=JSON.parseObject((String)row.get("request_json"),ImportFollowupRequest.class);
        if(row.get("review_id")!=null)request.setReviewers(targetReviewers((String)row.get("project_id"),(String)row.get("review_id")));
        permissions((String)row.get("project_id"),actor,request.getReviewers(),request.getPolicy());
        checkTokenPolicy(sourceSnapshot(row,operation),actor);
        if(row.get("plan_id")!=null&&row.get("review_id")!=null)validateTargets((String)row.get("project_id"),(String)row.get("plan_id"),(String)row.get("review_id"));
        jdbc.update("UPDATE plan_review_import_followup SET state='PENDING',error_message=NULL,update_time=? WHERE job_id=?",System.currentTimeMillis(),row.get("job_id"));
        jdbc.update("UPDATE plan_review_operation SET state='PENDING' WHERE id=?",operation.get("id"));
    }
    public void takeoverOperation(Map<String,Object> operation,String actor,String approver){
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Import takeover requires its operation transaction");
        var rows=jdbc.queryForList("SELECT * FROM plan_review_import_followup WHERE operation_id=? AND project_id=? FOR UPDATE",operation.get("id"),operation.get("project_id"));
        if(rows.size()!=1 || Set.of("SUCCEEDED","SKIPPED","TERMINATED").contains(rows.getFirst().get("state")))throw new io.metersphere.functional.planreview.PlanReviewFailure("OPERATION_FINISHED",409,"Import followup cannot be taken over");
        var row=rows.getFirst();
        var children=jdbc.queryForList("SELECT * FROM plan_review_operation WHERE project_id=? AND source_kind='PLAN_SERVICE' AND operation_type='CREATE_PAIR' AND request_key=? FOR UPDATE",operation.get("project_id"),"import:"+row.get("job_id"));
        for(var child:children){
            if("COMPLETED".equals(child.get("state"))){
                if(!Objects.equals(child.get("plan_id"),row.get("plan_id"))||!Objects.equals(child.get("review_id"),row.get("review_id"))||!Objects.equals(child.get("generation"),operation.get("generation")))throw new io.metersphere.functional.planreview.PlanReviewFailure("CHILD_OPERATION_CHANGED",409,"Completed import child does not match parent facts");
                continue;
            }
            if(!Objects.equals(child.get("executing_actor"),operation.get("executing_actor"))||!Objects.equals(child.get("executing_credential_id"),operation.get("executing_credential_id"))||!Objects.equals(child.get("generation"),operation.get("generation"))||"TERMINATED".equals(child.get("state")))throw new io.metersphere.functional.planreview.PlanReviewFailure("CHILD_OPERATION_CHANGED",409,"Reserved import child ownership or generation changed");
            jdbc.update("UPDATE plan_review_operation SET executing_actor=?,executing_credential_id=NULL,approver=?,revision=revision+1,update_time=? WHERE id=?",actor,approver,System.currentTimeMillis(),child.get("id"));
        }
    }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void terminateOperation(Map<String,Object> operation,String actor){
        var rows=jdbc.queryForList("SELECT * FROM plan_review_import_followup WHERE operation_id=? AND project_id=? FOR UPDATE",operation.get("id"),operation.get("project_id"));
        if(rows.size()!=1)throw new io.metersphere.functional.planreview.PlanReviewFailure("OPERATION_CHANGED",409,"原导入后处理任务不可用");
        var row=rows.getFirst();
        if(Set.of("SUCCEEDED","SKIPPED").contains(row.get("state")))throw new io.metersphere.functional.planreview.PlanReviewFailure("OPERATION_FINISHED",409,"已成功的导入后处理不能终止");
        var children=jdbc.queryForList("SELECT * FROM plan_review_operation WHERE project_id=? AND source_kind='PLAN_SERVICE' AND operation_type='CREATE_PAIR' AND request_key=? AND stage IN ('VALIDATED','LEGACY_PLAN_CREATED') FOR UPDATE",operation.get("project_id"),"import:"+row.get("job_id"));
        for(var child:children){
            if(!Objects.equals(child.get("executing_actor"),operation.get("executing_actor"))||!Objects.equals(child.get("executing_credential_id"),operation.get("executing_credential_id")))throw new io.metersphere.functional.planreview.PlanReviewFailure("CHILD_OPERATION_CHANGED",409,"原创建子步骤执行身份不匹配，终止未生效");
            jdbc.update("UPDATE plan_review_operation SET state='TERMINATED',revision=revision+1,update_time=? WHERE id=?",System.currentTimeMillis(),child.get("id"));
        }
        jdbc.update("UPDATE plan_review_import_followup SET state='TERMINATED',error_message=NULL,update_time=? WHERE operation_id=? AND project_id=?",System.currentTimeMillis(),operation.get("id"),operation.get("project_id"));
    }
    private static String payloadHash(String payload){
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }

    private void prepareImportReservation(String id){
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Reservation must precede the business transaction");
        var rows=jdbc.queryForList("SELECT * FROM plan_review_import_followup WHERE job_id=? AND state='PENDING'",id);
        if(rows.isEmpty())return;var row=rows.getFirst();
        if(row.get("plan_id")!=null || !"CREATE_NEW_PAIR".equals(row.get("policy")))return;
        boolean asset="ASSET".equals(row.get("source_kind"));
        String countSql=asset?"SELECT COUNT(*) FROM case_asset_import_result WHERE job_id=? AND status='SUCCESS' AND target_case_id IS NOT NULL":"SELECT COUNT(*) FROM functional_import_item WHERE job_id=? AND state='SUCCESS' AND target_id IS NOT NULL";
        if(jdbc.queryForObject(countSql,Long.class,id)==0)return;
        var operation=root(row,false);if(Set.of("TERMINATED","COMPLETED").contains(operation.get("state")))return;String user=(String)operation.get("executing_actor"),project=(String)row.get("project_id");
        var job=sourceSnapshot(row,operation);checkTokenPolicy(job,user);
        var request=JSON.parseObject((String)row.get("request_json"),ImportFollowupRequest.class);
        if(row.get("review_id")!=null)request.setReviewers(targetReviewers(project,(String)row.get("review_id")));
        permissions(project,user,request.getReviewers(),request.getPolicy());
        runWithTokenPolicy(job,user,()->plans.reserveCreation(creationRequest(id,project,job,request),user));
    }

    static TestPlanCreateRequest creationRequest(String id,String project,Map<String,Object> job,ImportFollowupRequest request){
        var create=new TestPlanCreateRequest();create.setProjectId(project);create.setName(name(request.getPlanName(),String.valueOf(job.get("source_name")),"测试计划",id));
        create.setRequestKey("import:"+id);
        create.setReviewSettings(new io.metersphere.functional.planreview.PlanReviewSettings(
                request.getReviewName()==null||request.getReviewName().isBlank()?io.metersphere.functional.planreview.PlanReviewSettings.defaultName(create.getName()):request.getReviewName(),
                request.getReviewModuleId(),request.getReviewers(),request.getReviewPassRule()));
        return create;
    }

    private void process(String id) {
        var hints=jdbc.queryForList("SELECT * FROM plan_review_import_followup WHERE job_id=? AND state='PENDING'",id);
        if(hints.isEmpty())return;var hint=hints.getFirst();String project=(String)hint.get("project_id");
        jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE",project);
        var operation=root(hint,true);
        var rows=jdbc.queryForList("SELECT * FROM plan_review_import_followup WHERE job_id=? AND state='PENDING' AND project_id=? FOR UPDATE",id,project);
        if(rows.isEmpty())return;var row=rows.getFirst();
        if(!Objects.equals(row.get("operation_id"),operation.get("id")))throw AgentBusinessAccess.denied();
        if("TERMINATED".equals(operation.get("state"))){jdbc.update("UPDATE plan_review_import_followup SET state='TERMINATED' WHERE job_id=?",id);return;}
        if("COMPLETED".equals(operation.get("state"))){jdbc.update("UPDATE plan_review_import_followup_item SET review_done=1 WHERE job_id=? AND plan_done=1",id);jdbc.update("UPDATE plan_review_import_followup SET state='SUCCEEDED' WHERE job_id=?",id);return;}
        if(operation.get("relation_id")!=null && jdbc.queryForList("SELECT id FROM plan_review_relation WHERE id=? AND generation=? AND project_id=? AND state='ACTIVE' FOR UPDATE",operation.get("relation_id"),operation.get("generation"),project).size()!=1){
            jdbc.update("UPDATE plan_review_import_followup SET state='TERMINATED' WHERE job_id=?",id);progress(row,"TERMINATED","TERMINATED");return;
        }
        String user=(String)operation.get("executing_actor");var job=sourceSnapshot(row,operation);checkTokenPolicy(job,user);
        var request=JSON.parseObject((String)row.get("request_json"),ImportFollowupRequest.class);
        if(row.get("review_id")!=null)request.setReviewers(targetReviewers(project,(String)row.get("review_id")));
        permissions(project,user,request.getReviewers(),request.getPolicy());
        if("APPEND_EXISTING".equals(request.getPolicy())) {
            validateTargets(project,(String)row.get("plan_id"),(String)row.get("review_id"));
            request.setReviewers(targetReviewers(project,(String)row.get("review_id")));
        }
        if("ASSET".equals(row.get("source_kind")))jdbc.update("INSERT IGNORE INTO plan_review_import_followup_item(job_id,case_id) SELECT job_id,target_case_id FROM case_asset_import_result WHERE job_id=? AND status='SUCCESS' AND target_case_id IS NOT NULL",id);
        else jdbc.update("INSERT IGNORE INTO plan_review_import_followup_item(job_id,case_id) SELECT job_id,target_id FROM functional_import_item WHERE job_id=? AND state='SUCCESS' AND target_id IS NOT NULL",id);
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_import_followup_item WHERE job_id=?",Long.class,id);
        if(total==null||total==0){jdbc.update("UPDATE plan_review_import_followup SET state='SUCCEEDED',update_time=? WHERE job_id=?",System.currentTimeMillis(),id);progress(row,"SKIPPED_ZERO_SUCCESS","COMPLETED");return;}
        String plan=(String)row.get("plan_id"),review=(String)row.get("review_id");
        if(plan==null) {
            var create=creationRequest(id,project,job,request);
            var created=new java.util.concurrent.atomic.AtomicReference<io.metersphere.plan.domain.TestPlan>();
            runWithTokenPolicy(job,user,()->created.set(plans.addFromReservation(create,user,"/ai/case-imports/followup","POST")));
            plan=created.get().getId();
            var relation=jdbc.queryForMap("SELECT id,review_id,generation FROM plan_review_relation WHERE active_plan_id=? AND project_id=?",plan,project);
            jdbc.update("UPDATE plan_review_import_followup SET plan_id=?,review_id=?,relation_id=?,generation=?,update_time=? WHERE job_id=?",plan,relation.get("review_id"),relation.get("id"),relation.get("generation"),System.currentTimeMillis(),id);
            jdbc.update("UPDATE plan_review_operation SET plan_id=?,review_id=?,relation_id=?,generation=? WHERE id=?",plan,relation.get("review_id"),relation.get("id"),relation.get("generation"),row.get("operation_id"));
            progress(row,"SOURCE_APPENDING","PENDING");return;
        }
        if(review==null) {
            var create=creationRequest(id,project,job,request);
            runWithTokenPolicy(job,user,()->plans.completeLegacyImportReview(create,user));
            var relation=jdbc.queryForMap("SELECT id,review_id,generation FROM plan_review_relation WHERE active_plan_id=? AND project_id=?",plan,project);
            jdbc.update("UPDATE plan_review_import_followup SET review_id=?,relation_id=?,generation=?,update_time=? WHERE job_id=?",relation.get("review_id"),relation.get("id"),relation.get("generation"),System.currentTimeMillis(),id);
            jdbc.update("UPDATE plan_review_operation SET review_id=?,relation_id=?,generation=? WHERE id=?",relation.get("review_id"),relation.get("id"),relation.get("generation"),row.get("operation_id"));
            progress(row,"SOURCE_APPENDING","PENDING");return;
        }
        validateTargets(project,plan,review);
        var current=jdbc.queryForList("SELECT * FROM plan_review_relation WHERE id=? AND generation=? AND active_plan_id=? AND active_review_id=? AND project_id=? AND state='ACTIVE' FOR UPDATE",row.get("relation_id"),row.get("generation"),plan,review,project);
        if(current.size()!=1)throw new MSException("原关联已结束，本次导入不会写入新关系");
        var ids=jdbc.queryForList("SELECT case_id FROM plan_review_import_followup_item WHERE job_id=? AND plan_done=0 ORDER BY case_id LIMIT 100",String.class,id);
        if(ids.isEmpty()){
            var relation=current.getFirst();
            if("SYNCED".equals(relation.get("sync_state")) && Objects.equals(relation.get("source_version"),relation.get("applied_version"))) {
                jdbc.update("UPDATE plan_review_import_followup_item SET review_done=1 WHERE job_id=? AND plan_done=1",id);
                jdbc.update("UPDATE plan_review_import_followup SET state='SUCCEEDED',update_time=? WHERE job_id=?",System.currentTimeMillis(),id);
                progress(row,"COMPLETED","COMPLETED");return;
            } else if(Set.of("FAILED_MANUAL","RECONFIRM_REQUIRED").contains(relation.get("sync_state"))) {
                throw new MSException("源计划已保存，评审尚未追平；请在关联卡重新预览确认，勿重复导入");
            }
            if(!"TARGET_PENDING".equals(operation.get("stage")))progress(row,"TARGET_PENDING","PENDING");
            return;
        }
        access.cases(project,ids);
        var session=SessionUser.fromUser(users.getUserDTO(user),"");session.setLastProjectId(project);
        session.setLastOrganizationId(jdbc.queryForObject("SELECT organization_id FROM project WHERE id=?",String.class,project));
        var associate=new TestPlanFunctionalCaseAssociateRequest();associate.setTestPlanId(plan);associate.setProjectId(project);associate.setSelectIds(ids);planCases.associateCases(associate,session);
        for(String caseId:ids)jdbc.update("UPDATE plan_review_import_followup_item SET plan_done=1 WHERE job_id=? AND case_id=?",id,caseId);
        jdbc.update("UPDATE plan_review_import_followup SET update_time=? WHERE job_id=?",System.currentTimeMillis(),id);
        progress(row,"SOURCE_APPENDING","PENDING");
    }

    private void checkTokenPolicy(Map<String,Object> job,String user) {runWithTokenPolicy(job,user,()->{});}
    private void runWithTokenPolicy(Map<String,Object> job,String user,Runnable action) {
        runWithTokenPolicy(job,user,false,action);
    }
    private void runWithTokenPolicy(Map<String,Object> job,String user,boolean importOnly,Runnable action) {
        String token=Objects.toString(job.get("token_id"),"WEB");if("WEB".equals(token)){
            var previous=io.metersphere.agent.security.AgentTokenContext.get();
            try{io.metersphere.agent.security.AgentTokenContext.clear();action.run();}
            finally{if(previous!=null)io.metersphere.agent.security.AgentTokenContext.set(previous);else io.metersphere.agent.security.AgentTokenContext.clear();}
            return;
        }
        boolean locking=org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();
        if(locking)relationAuthorization.current(user,(String)job.get("project_id"),true);
        var tokens=jdbc.query("SELECT id,user_id,enable,status,revoked_at,expire_time,project_ids,project_id,scopes FROM agent_token WHERE id=? AND user_id=?"+(locking?" FOR UPDATE":""),org.springframework.jdbc.core.BeanPropertyRowMapper.newInstance(io.metersphere.system.domain.AgentToken.class),token,user);
        if(tokens.size()!=1)throw AgentBusinessAccess.denied();
        if(!(importOnly?AgentPlanReviewExecutionCredential.identityAllowed(tokens.getFirst(),(String)job.get("project_id"),user):AgentPlanReviewExecutionCredential.allowed(tokens.getFirst(),(String)job.get("project_id"),user))
                ||!io.metersphere.agent.security.AgentTokenScopeParser.hasScope(tokens.getFirst().getScopes(),io.metersphere.agent.constants.AgentTokenScope.CASE_IMPORT_V2))throw AgentBusinessAccess.denied();
        var previous=io.metersphere.agent.security.AgentTokenContext.get();
        try {
            io.metersphere.agent.security.AgentTokenContext.set(tokens.getFirst());
            if(importOnly)access.project((String)job.get("project_id"),PermissionConstants.FUNCTIONAL_CASE_READ_IMPORT,true,io.metersphere.agent.constants.AgentTokenScope.CASE_IMPORT_V2);
            else{
                access.project((String)job.get("project_id"),PermissionConstants.TEST_PLAN_READ_ASSOCIATION,true,io.metersphere.agent.constants.AgentTokenScope.PLAN_WRITE);
                access.project((String)job.get("project_id"),PermissionConstants.CASE_REVIEW_READ,true,io.metersphere.agent.constants.AgentTokenScope.REVIEW_WRITE);
            }
            action.run();
        } finally {
            if(previous==null)io.metersphere.agent.security.AgentTokenContext.clear();else io.metersphere.agent.security.AgentTokenContext.set(previous);
        }
    }

    private static String name(String chosen,String file,String suffix,String id) {
        return org.apache.commons.lang3.StringUtils.left(chosen==null||chosen.isBlank()?file+"-"+suffix+"-"+id.substring(0,Math.min(8,id.length())):chosen.trim(),255);
    }
}
