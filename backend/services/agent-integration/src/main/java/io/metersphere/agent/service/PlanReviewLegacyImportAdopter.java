package io.metersphere.agent.service;

import io.metersphere.agent.dto.ImportFollowupRequest;
import io.metersphere.functional.planreview.*;
import io.metersphere.sdk.util.JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Transfers original import identities and per-case watermarks, without replaying primary imports. */
@Service
public class PlanReviewLegacyImportAdopter implements PlanReviewLegacyJobAdopter {
    private final JdbcTemplate jdbc;
    private final PlanReviewAuthorization auth;
    private final PlanReviewPlanStateResolver states;
    private boolean sandbox;
    public PlanReviewLegacyImportAdopter(JdbcTemplate jdbc){this(jdbc,new PlanReviewPlanStateResolver(List.of()));}
    @org.springframework.beans.factory.annotation.Autowired
    public PlanReviewLegacyImportAdopter(JdbcTemplate jdbc,PlanReviewPlanStateResolver states){this.jdbc=jdbc;this.auth=new PlanReviewAuthorization(jdbc);this.states=states;}
    @Override public Map<String,Object> preview(String project,Map<String,Object> old,PlanReviewLegacyDecision.Job decision){
        String id=(String)old.get("job_id"),original=(String)old.get("user_id");
        if(old.get("source_kind")==null||!Set.of("FILE","ASSET").contains(old.get("source_kind")))throw fail("旧来源类型未知，请先隔离核实，不能推断为文件导入");
        if(!project.equals(old.get("project_id"))||!Set.of("CREATE_NEW_PAIR","APPEND_EXISTING").contains(old.get("policy")))throw fail("原任务不是当前项目可移交的关联导入");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_import_followup WHERE job_id=?",Integer.class,id)!=0)throw fail("原任务已移交过，不会绑定到另一个关系代次");
        var source=source(project,old);var success=success(old);
        var request=parseRequest((String)old.get("request_json"));
        if(!Objects.equals(request.getPolicy(),old.get("policy")))throw fail("原任务策略与已保存请求矛盾，请先隔离");
        String plan=(String)old.get("plan_id"),review=(String)old.get("review_id");
        if("SUCCEEDED".equals(old.get("state"))&&!success.isEmpty()&&(plan==null||review==null))throw fail("旧成功状态与对象记录矛盾，请先隔离核实，不能冒充已完成");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup_item WHERE job_id=? AND review_done=1 AND plan_done=0",Integer.class,id)>0)throw fail("旧任务进度矛盾，请先隔离核实");
        for(String caseId:jdbc.queryForList("SELECT case_id FROM functional_import_followup_item WHERE job_id=?",String.class,id))if(!success.contains(caseId))throw fail("旧成功水位不属于原导入的成功项，请先隔离核实");
        if(plan==null&&review!=null)throw fail("旧任务存在无源计划的评审记录，只能先隔离或终止");
        if("APPEND_EXISTING".equals(old.get("policy"))&&(plan==null||review==null))throw fail("追加任务没有完整原目标，不会重新选择目标");
        if(plan!=null)requireObject("test_plan",plan,project);
        if(review!=null)requireObject("case_review",review,project);
        if(plan!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation WHERE project_id=? AND (plan_id=? OR review_id=?)",Integer.class,project,plan,review)>0)throw fail("原对象已有新协议关系历史，不会重配已结束代次或接入另一条关系");
        if(plan!=null&&review==null&&!jdbc.queryForList("SELECT id FROM plan_review_relation WHERE active_plan_id=? FOR UPDATE",plan).isEmpty())throw fail("原计划已有其他新关联，不会补建评审");
        if(review==null&&!success.isEmpty()){
            var saved=JSON.parseObject((String)old.get("request_json"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
            if(decision.reviewSettings()!=null){
                var fill=decision.reviewSettings();
                unchanged(saved,"reviewName",fill.name());unchanged(saved,"reviewModuleId",fill.moduleId());unchanged(saved,"reviewers",fill.reviewers());unchanged(saved,"reviewPassRule",fill.reviewPassRule());
                request.setReviewName(fill.name());request.setReviewModuleId(fill.moduleId());request.setReviewers(fill.reviewers());request.setReviewPassRule(fill.reviewPassRule());
            }else for(String field:List.of("reviewName","reviewModuleId","reviewers","reviewPassRule"))if(!saved.containsKey(field)||saved.get(field)==null||saved.get(field) instanceof String text&&text.isBlank()||saved.get(field) instanceof Collection<?> collection&&collection.isEmpty())throw fail("旧记录缺少明确评审设置，请显式补齐；不会沿用DTO的根目录或规则默认值");
            // Constructor enforces complete explicit settings; absent historical fields are never silently defaulted here.
            try{new PlanReviewSettings(request.getReviewName(),request.getReviewModuleId(),request.getReviewers(),request.getReviewPassRule());}catch(IllegalArgumentException invalid){throw fail("旧评审设置不完整或非法，请显式补齐有效设置");}
            module(project,request.getReviewModuleId());reviewers(project,request.getReviewers());
        }else if(review!=null)request.setReviewers(jdbc.queryForList("SELECT user_id FROM case_review_user WHERE review_id=? ORDER BY user_id",String.class,review));
        var access=auth.current(decision.executingActor(),project,true);
        boolean create="CREATE_NEW_PAIR".equals(request.getPolicy());int count=success.isEmpty()?0:1;
        if(!access.permissions().contains(PlanReviewPolicy.Permission.IMPORT)||!(create?PlanReviewPolicy.allowedCreatePair(access,count):PlanReviewPolicy.allowed(PlanReviewPolicy.Action.APPEND,access)))throw fail("当前执行人缺少原导入策略的完整权限");
        if(review!=null||!success.isEmpty())reviewers(project,request.getReviewers());
        String credential=Objects.toString(source.get("token_id"),"WEB");if("WEB".equals(credential))credential=null;
        if(!original.equals(decision.executingActor())&&!"WEB".equals(decision.executionMode()))throw fail("接手人不同，必须明确选择负责人批准的网页接手");
        String executing="WEB".equals(decision.executionMode())?null:credential;
        if(executing!=null){
            var token=jdbc.query("SELECT id,user_id,enable,status,revoked_at,expire_time,project_ids,project_id,scopes FROM agent_token WHERE id=?",org.springframework.jdbc.core.BeanPropertyRowMapper.newInstance(io.metersphere.system.domain.AgentToken.class),executing);
            if(token.size()!=1||!AgentPlanReviewExecutionCredential.allowed(token.getFirst(),project,decision.executingActor())||!io.metersphere.agent.security.AgentTokenScopeParser.hasScope(token.getFirst().getScopes(),io.metersphere.agent.constants.AgentTokenScope.CASE_IMPORT_V2))throw fail("原认证已失效；须显式选择经负责人批准的网页接手，不会借用其他凭据");
        }
        var prepared=new TreeMap<String,Object>();prepared.put("operationId",uuid());prepared.put("childOperationId",uuid());prepared.put("planId",plan==null?uuid():plan);prepared.put("reviewId",review==null?uuid():review);prepared.put("stage",plan==null?"NO_PLAN":review==null?"PLAN_ONLY":"FIXED_PAIR");
        prepared.put("executingActor",decision.executingActor());prepared.put("originalCredential",credential);prepared.put("executingCredential",executing);prepared.put("request",JSON.toJSONString(request));prepared.put("source",source);prepared.put("successIds",success);prepared.put("initialMemberCount",success.size());prepared.put("executionMode",decision.executionMode());
        prepared.put("permissions",PlanReviewAuthorization.resourceIds(access));
        var caseMetadata=new ArrayList<Map<String,Object>>();for(String caseId:success){var found=jdbc.queryForList("SELECT id,project_id,deleted FROM functional_case WHERE id=?",caseId);if(found.size()!=1||!project.equals(found.getFirst().get("project_id")))throw fail("原成功项不属于当前项目或记录缺失，不会跨项目移交");caseMetadata.add(new TreeMap<>(found.getFirst()));}prepared.put("caseMetadata",caseMetadata);
        if(executing!=null)prepared.put("tokenMetadata",jdbc.queryForMap("SELECT id,user_id,enable,status,revoked_at,expire_time,project_ids,project_id,scopes FROM agent_token WHERE id=?",executing));
        return prepared;
    }
    @Override public Map<String,Object> adopt(String project,String change,Map<String,Object> old,Map<String,Object> prepared,Map<String,Object> relation,String approvedBy){
        String id=(String)old.get("job_id"),actor=(String)prepared.get("executingActor");
        if(!JSON.toJSONString(prepared.get("source")).equals(JSON.toJSONString(source(project,old)))||!JSON.toJSONString(prepared.get("successIds")).equals(JSON.toJSONString(success(old))))throw fail("原导入状态或成功项变化，停止旧清单的迁移");
        var request=JSON.parseObject((String)prepared.get("request"),ImportFollowupRequest.class);var current=auth.current(actor,project,true);
        boolean create="CREATE_NEW_PAIR".equals(request.getPolicy());int count=((Number)prepared.get("initialMemberCount")).intValue();
        if(!current.permissions().contains(PlanReviewPolicy.Permission.IMPORT)||!(create?PlanReviewPolicy.allowedCreatePair(current,count):PlanReviewPolicy.allowed(PlanReviewPolicy.Action.APPEND,current)))throw fail("执行人当前权限发生变化，移交未生效");
        if(prepared.get("executingCredential")!=null){
            var tokens=jdbc.query("SELECT id,user_id,enable,status,revoked_at,expire_time,project_ids,project_id,scopes FROM agent_token WHERE id=? FOR UPDATE",org.springframework.jdbc.core.BeanPropertyRowMapper.newInstance(io.metersphere.system.domain.AgentToken.class),prepared.get("executingCredential"));
            if(tokens.size()!=1||!AgentPlanReviewExecutionCredential.allowed(tokens.getFirst(),project,actor)||!io.metersphere.agent.security.AgentTokenScopeParser.hasScope(tokens.getFirst().getScopes(),io.metersphere.agent.constants.AgentTokenScope.CASE_IMPORT_V2))throw fail("原认证在预览后失效，移交已停止");
        }
        if(old.get("review_id")!=null||count>0)reviewers(project,request.getReviewers());if(old.get("review_id")==null&&count>0)module(project,request.getReviewModuleId());
        if(old.get("review_id")!=null&&relation.isEmpty())throw fail("原完整目标没有明确保留的同一关联，不会重配目标");
        String op=(String)prepared.get("operationId"),plan=(String)prepared.get("planId"),review=(String)prepared.get("reviewId");long now=System.currentTimeMillis();
        boolean completed="SUCCEEDED".equals(old.get("state"));String state=completed?"COMPLETED":count==0?"COMPLETED":"FAILED";
        jdbc.update("INSERT INTO plan_review_operation(id,project_id,source_kind,operation_type,request_key,payload_hash,request_json,original_actor,executing_actor,original_credential_id,executing_credential_id,approver,initial_member_count,plan_id,review_id,relation_id,generation,stage,state,safe_error,create_time,update_time) VALUES(?,?,'IMPORT_FOLLOWUP',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                op,project,create?"CREATE_PAIR":"APPEND",id,hash((String)prepared.get("request")),prepared.get("request"),old.get("user_id"),actor,prepared.get("originalCredential"),prepared.get("executingCredential"),approvedBy,count,plan,review,relation.get("relationId"),relation.get("generation"),completed?"COMPLETED":count==0?"SKIPPED_ZERO_SUCCESS":prepared.get("stage"),state,"COMPLETED".equals(state)?null:"历史任务已移交，需显式恢复原操作",now,now);
        jdbc.update("INSERT INTO plan_review_import_followup(job_id,project_id,user_id,operation_id,policy,request_json,group_id,plan_id,review_id,relation_id,generation,state,error_message,create_time,update_time,source_kind) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,project,old.get("user_id"),op,old.get("policy"),prepared.get("request"),old.get("group_id"),old.get("plan_id"),old.get("review_id"),relation.get("relationId"),relation.get("generation"),"COMPLETED".equals(state)?"SUCCEEDED":"FAILED","COMPLETED".equals(state)?null:"等待显式恢复原任务",old.get("create_time"),now,old.get("source_kind"));
        jdbc.update("INSERT INTO plan_review_import_followup_item(job_id,case_id,plan_done,review_done) SELECT job_id,case_id,plan_done,review_done FROM functional_import_followup_item WHERE job_id=?",id);
        for(String caseId:strings(prepared.get("successIds")))jdbc.update("INSERT IGNORE INTO plan_review_import_followup_item(job_id,case_id) VALUES(?,?)",id,caseId);
        if(create&&old.get("review_id")==null&&count>0){
            var createRequest=ImportFollowupCoordinator.creationRequest(id,project,map(prepared.get("source")),request);String payload=JSON.toJSONString(createRequest);
            jdbc.update("INSERT INTO plan_review_operation(id,project_id,source_kind,operation_type,request_key,payload_hash,request_json,original_actor,executing_actor,original_credential_id,executing_credential_id,approver,initial_member_count,plan_id,review_id,stage,state,create_time,update_time) VALUES(?,?,'PLAN_SERVICE','CREATE_PAIR',?,?,?,?,?,?,?,?,?,?,?,?, 'PENDING',?,?)",
                    prepared.get("childOperationId"),project,"import:"+id,hash(payload),payload,old.get("user_id"),actor,prepared.get("originalCredential"),prepared.get("executingCredential"),approvedBy,count,plan,review,old.get("plan_id")==null?"VALIDATED":"LEGACY_PLAN_CREATED",now,now);
        }
        return Map.of("jobId",id,"operationId",op,"planId",plan,"reviewId",review,"state",state,"automaticResume",false);
    }
    @Override public void stop(String project,String change){
        var jobs=jdbc.queryForList("SELECT job_id,operation_id FROM plan_review_legacy_job_map WHERE project_id=? AND change_id=? AND state='ADOPTED' FOR UPDATE",project,change);
        for(var job:jobs){
            jdbc.update("UPDATE plan_review_import_followup SET state='TERMINATED' WHERE job_id=? AND project_id=? AND state<>'SUCCEEDED'",job.get("job_id"),project);
            jdbc.update("UPDATE plan_review_operation SET state='TERMINATED',revision=revision+1,update_time=? WHERE project_id=? AND (id=? OR source_kind='PLAN_SERVICE' AND request_key=?) AND state<>'COMPLETED'",System.currentTimeMillis(),project,job.get("operation_id"),"import:"+job.get("job_id"));
        }
    }
    @Override public PlanReviewLegacyJobAdopter forDatabase(JdbcTemplate isolated){var clone=new PlanReviewLegacyImportAdopter(isolated);clone.sandbox=true;return clone;}
    @Override public void prepareSandbox(Map<String,Object> snapshot){
        if(!sandbox)throw new IllegalStateException("Rehearsal fixtures are forbidden on the native project adapter");
        for(String ddl:List.of(
                "CREATE TABLE functional_import_job(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),user_id VARCHAR(50),source_name VARCHAR(255),state VARCHAR(32),token_id VARCHAR(50))",
                "CREATE TABLE functional_import_item(job_id VARCHAR(50),target_id VARCHAR(50),state VARCHAR(32))",
                "CREATE TABLE default_hub_sync_job(id VARCHAR(50) PRIMARY KEY,scope_project_id VARCHAR(50),create_user VARCHAR(50),job_type VARCHAR(32),status VARCHAR(32))",
                "CREATE TABLE case_asset_import_result(job_id VARCHAR(50),target_case_id VARCHAR(50),status VARCHAR(32))",
                "CREATE TABLE case_review_module(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50))",
                "CREATE TABLE agent_token(id VARCHAR(50) PRIMARY KEY,user_id VARCHAR(50),enable BOOLEAN,status VARCHAR(32),revoked_at BIGINT,expire_time BIGINT,project_ids TEXT,project_id VARCHAR(50),scopes TEXT)"))jdbc.execute(ddl);
        var data=map(snapshot.get("data"));String project=(String)data.get("projectId");var prepared=map(snapshot.get("preparedJobs"));
        for(var old:maps(data.get("jobs"))){
            if(!prepared.containsKey(old.get("job_id")))continue;var info=map(prepared.get(old.get("job_id")));var source=map(info.get("source"));var request=JSON.parseObject((String)info.get("request"),ImportFollowupRequest.class);
            boolean asset="ASSET".equals(old.get("source_kind"));
            if(asset)jdbc.update("INSERT INTO default_hub_sync_job VALUES(?,?,?,'IMPORT_CASE',?)",old.get("job_id"),project,old.get("user_id"),source.get("state"));
            else jdbc.update("INSERT INTO functional_import_job VALUES(?,?,?,?,?,?)",old.get("job_id"),project,old.get("user_id"),source.get("source_name"),source.get("state"),source.get("token_id"));
            for(String id:strings(info.get("successIds")))jdbc.update(asset?"INSERT INTO case_asset_import_result VALUES(?,?,'SUCCESS')":"INSERT INTO functional_import_item VALUES(?,?,'SUCCESS')",old.get("job_id"),id);
            seedActor((String)info.get("executingActor"),project,strings(info.get("permissions")));
            if(request.getReviewers()!=null)for(String reviewer:request.getReviewers())seedActor(reviewer,project,List.of("CASE_REVIEW:READ","CASE_REVIEW:READ+REVIEW"));
            if(request.getReviewModuleId()!=null&&!"root".equals(request.getReviewModuleId()))jdbc.update("INSERT IGNORE INTO case_review_module VALUES(?,?)",request.getReviewModuleId(),project);
            for(var fact:maps(info.get("caseMetadata")))jdbc.update("INSERT IGNORE INTO functional_case(id,project_id,deleted) VALUES(?,?,?)",fact.get("id"),fact.get("project_id"),fact.get("deleted"));
            if(info.get("tokenMetadata")!=null){var token=map(info.get("tokenMetadata"));var columns=new TreeSet<>(token.keySet());jdbc.update("INSERT IGNORE INTO agent_token("+String.join(",",columns)+") VALUES("+String.join(",",Collections.nCopies(columns.size(),"?"))+")",columns.stream().map(token::get).toArray());}
        }
    }
    private void seedActor(String actor,String project,List<String> permissions){
        jdbc.update("INSERT IGNORE INTO user VALUES(?,1,0)",actor);String role="fixture-"+Integer.toUnsignedString(actor.hashCode());
        jdbc.update("INSERT IGNORE INTO user_role VALUES(?,'PROJECT',1)",role);jdbc.update("INSERT INTO user_role_relation VALUES(?,?,?)",actor,role,project);
        for(String permission:permissions)jdbc.update("INSERT INTO user_role_permission VALUES(?,?)",role,permission);
    }
    private Map<String,Object> source(String project,Map<String,Object> old){
        boolean asset="ASSET".equals(old.get("source_kind"));var rows=jdbc.queryForList(asset?"SELECT scope_project_id project_id,'Asset import' source_name,status state,'WEB' token_id FROM default_hub_sync_job WHERE id=? AND create_user=? AND scope_project_id=? AND job_type='IMPORT_CASE'":"SELECT project_id,source_name,state,token_id FROM functional_import_job WHERE id=? AND user_id=? AND project_id=?",old.get("job_id"),old.get("user_id"),project);
        if(rows.size()!=1||!(asset?Set.of("SUCCESS","PARTIAL_SUCCESS","FAILED"):Set.of("COMPLETED","PARTIAL","STOPPED")).contains(rows.getFirst().get("state")))throw fail("原导入尚未结束或来源身份不匹配，须先停入口并核实提交结果");return new TreeMap<>(rows.getFirst());
    }
    private List<String> success(Map<String,Object> old){return jdbc.queryForList("ASSET".equals(old.get("source_kind"))?"SELECT DISTINCT target_case_id FROM case_asset_import_result WHERE job_id=? AND status='SUCCESS' AND target_case_id IS NOT NULL ORDER BY target_case_id":"SELECT DISTINCT target_id FROM functional_import_item WHERE job_id=? AND state='SUCCESS' AND target_id IS NOT NULL ORDER BY target_id",String.class,old.get("job_id"));}
    private void requireObject(String table,String id,String project){
        String type="test_plan".equals(table)?" AND type='TEST_PLAN'":"";
        if(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=? AND project_id=?"+type,Integer.class,id,project)!=1)throw fail("原成功对象已删除或不属于当前项目，不会重建或换目标");
        var row=jdbc.queryForMap("SELECT id,project_id,status FROM "+table+" WHERE id=? FOR UPDATE",id);if("test_plan".equals(table))row=states.resolve(row);
        if(!PlanReviewPolicy.State.parse((String)row.get("status")).editable())throw fail("原成功对象已锁定，须先按原流程恢复可编辑，不能由迁移自动解锁");
    }
    private void module(String project,String module){if(!"root".equals(module)&&jdbc.queryForObject("SELECT COUNT(*) FROM case_review_module WHERE id=? AND project_id=?",Integer.class,module,project)!=1)throw fail("原确认目录不存在，不会隐式改配目录");}
    private void reviewers(String project,List<String> reviewers){if(reviewers==null||reviewers.isEmpty()||reviewers.size()>100)throw fail("须明确一至一百名当前有效评审人员");for(String reviewer:new TreeSet<>(reviewers)){
        if(!auth.current(reviewer,project,true).permissions().contains(PlanReviewPolicy.Permission.REVIEW_OPINION))throw fail("评审人员已停用、无项目访问或不具备当前评审权限");}}
    private static void unchanged(Map<String,Object> saved,String key,Object value){Object prior=saved.get(key);if(prior!=null&&!(prior instanceof String text&&text.isBlank())&&!(prior instanceof Collection<?> collection&&collection.isEmpty())&&!JSON.toJSONString(prior).equals(JSON.toJSONString(value)))throw fail("只能补齐缺失的评审设置，不能改写此前明确确认的设置");}
    private static String hash(String value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private static String uuid(){return UUID.randomUUID().toString();}
    private static ImportFollowupRequest parseRequest(String json){try{var request=JSON.parseObject(json,ImportFollowupRequest.class);if(request==null)throw new IllegalArgumentException();return request;}catch(RuntimeException invalid){throw fail("旧任务请求不完整或格式非法，请先隔离核实");}}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    @SuppressWarnings("unchecked") private static List<String> strings(Object value){return (List<String>)value;}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> maps(Object value){return (List<Map<String,Object>>)value;}
    private static PlanReviewFailure fail(String message){return new PlanReviewFailure("LEGACY_JOB_ADOPTION_BLOCKED",409,message);}
}
