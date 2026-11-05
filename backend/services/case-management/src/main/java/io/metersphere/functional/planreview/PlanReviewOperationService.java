package io.metersphere.functional.planreview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Durable M06 controls. Neither an operation ID nor an approval substitutes for current rights. */
@Service
@Transactional(rollbackFor=Exception.class)
public class PlanReviewOperationService {
    private final JdbcTemplate jdbc;
    private final PlanReviewAuthorization authorization;
    private final List<PlanReviewOperationResumer> resumers;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private List<PlanReviewExecutionCredential> credentials=List.of();
    public PlanReviewOperationService(JdbcTemplate jdbc,PlanReviewAuthorization authorization,List<PlanReviewOperationResumer> resumers){
        this.jdbc=jdbc;this.authorization=authorization;this.resumers=List.copyOf(resumers);
    }
    private Map<String,Object> lock(String project,String id,String actor){
        authorization.current(actor,project,true); // grant first, then operation, then relation
        var rows=jdbc.queryForList("SELECT * FROM plan_review_operation WHERE id=? AND project_id=? FOR UPDATE",id,project);
        if(rows.size()!=1)throw fail("RESOURCE_NOT_ACCESSIBLE",404,"操作不可访问");
        return rows.getFirst();
    }
    private void owner(Map<String,Object> operation,String actor){
        if(!actor.equals(operation.get("executing_actor")))throw fail("RESOURCE_NOT_ACCESSIBLE",404,"操作不可访问");
    }
    private void revision(Map<String,Object> operation,long expected){
        if(number(operation,"revision")!=expected)throw fail("OPERATION_STALE",409,"操作已变化，请刷新后重试");
    }
    private void fence(Map<String,Object> operation){
        if("TERMINATED".equals(operation.get("state")))throw fail("OPERATION_TERMINATED",409,"原操作已终止");
        if(operation.get("relation_id")==null)return;
        var rows=jdbc.queryForList("SELECT generation,state FROM plan_review_relation WHERE id=? AND project_id=? FOR UPDATE",operation.get("relation_id"),operation.get("project_id"));
        if(rows.size()!=1 || !"ACTIVE".equals(rows.getFirst().get("state")) || !Objects.equals(operation.get("generation"),rows.getFirst().get("generation")))throw fail("RELATION_CHANGED",409,"原关联已结束，不会恢复或重新配对");
    }
    private void rights(Map<String,Object> operation,String actor){
        var access=authorization.current(actor,(String)operation.get("project_id"),true);
        if("IMPORT_FOLLOWUP".equals(operation.get("source_kind"))&&!access.permissions().contains(PlanReviewPolicy.Permission.IMPORT))throw fail("PERMISSION_REQUIRED",403,"导入后处理恢复还需要当前导入权限");
        String type=(String)operation.get("operation_type");
        boolean allowed=switch(type){
            case "CREATE_PAIR"->PlanReviewPolicy.allowedCreatePair(access,Math.toIntExact(number(operation,"initial_member_count")));
            case "COPY_PAIR"->PlanReviewPolicy.allowedCreatePair(access,members(operation));
            case "CREATE_REVIEW"->PlanReviewPolicy.allowed(PlanReviewPolicy.Action.CREATE_LINKED_REVIEW,access);
            case "COPY_GROUP"->access.hasAll(Set.of(PlanReviewPolicy.Permission.PLAN_ADD,PlanReviewPolicy.Permission.PLAN_READ));
            case "APPEND"->PlanReviewPolicy.allowed(PlanReviewPolicy.Action.APPEND,access);
            default->false;
        };
        if(!allowed)throw fail("PERMISSION_REQUIRED",403,"当前执行者缺少原创建或追加操作的完整权限");
    }
    private void credential(Map<String,Object> operation,String actor){
        if(((String)operation.get("request_key")).startsWith("import:"))throw fail("RECOVERY_CONTEXT_REQUIRED",409,"导入创建子步骤须连同原后处理任务一起恢复，不能单独继续");
        Object reference=operation.get("executing_credential_id");
        if(reference!=null && credentials.stream().noneMatch(provider->"IMPORT_FOLLOWUP".equals(operation.get("source_kind"))?provider.validateImport((String)reference,(String)operation.get("project_id"),actor):"CREATE_REVIEW".equals(operation.get("operation_type"))?provider.validateLinkedReview((String)reference,(String)operation.get("project_id"),actor):provider.validate((String)reference,(String)operation.get("project_id"),actor)))throw fail("CREDENTIAL_CONTEXT_REQUIRED",403,"恢复原Agent操作需其当前有效凭据上下文；不能自动替换为Web身份");
    }
    private int members(Map<String,Object> operation){
        String plan=(String)operation.get("plan_id");
        if("COPY_PAIR".equals(operation.get("operation_type"))){
            var payload=io.metersphere.sdk.util.JSON.parseObject((String)operation.get("request_json"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
            plan=Objects.toString(payload.get("sourceId"),"");
        }
        return new HashSet<>(jdbc.queryForList("SELECT m.functional_case_id FROM test_plan_functional_case m JOIN functional_case c ON c.id=m.functional_case_id WHERE m.test_plan_id=? AND c.project_id=? AND c.deleted=0 FOR UPDATE",String.class,plan,operation.get("project_id"))).size();
    }
    private void approver(String actor,String project){approver(actor,project,false);}
    private void approver(String actor,String project,boolean groupOnly){
        var required=java.util.EnumSet.of(PlanReviewPolicy.Permission.TAKEOVER_APPROVE,PlanReviewPolicy.Permission.PLAN_READ);
        if(!groupOnly)required.add(PlanReviewPolicy.Permission.REVIEW_READ);
        if(!authorization.current(actor,project,true).permissions().containsAll(required))throw fail("PERMISSION_REQUIRED",403,"Takeover approval and applicable object read permissions required");
    }
    public Map<String,Object> takeovers(String project,String actor){
        var access=authorization.current(actor,project,false);
        if(!access.permissions().contains(PlanReviewPolicy.Permission.PLAN_READ))throw fail("RESOURCE_NOT_ACCESSIBLE",404,"接管记录不可访问");
        boolean canApprove=access.permissions().contains(PlanReviewPolicy.Permission.TAKEOVER_APPROVE);
        var args=new ArrayList<Object>();args.add(project);
        String own=canApprove?"":" AND (t.old_actor=? OR t.new_actor=? OR t.requested_by=?)";
        if(!access.permissions().contains(PlanReviewPolicy.Permission.REVIEW_READ))own+=" AND o.operation_type='COPY_GROUP'";
        if(!canApprove){args.add(actor);args.add(actor);args.add(actor);}
        var rows=jdbc.queryForList("SELECT t.*,o.source_kind,o.operation_type,o.initial_member_count,o.plan_id,o.request_json,o.revision current_revision,o.state operation_state,o.stage,o.executing_actor,r.state relation_state,r.generation relation_generation FROM plan_review_takeover t JOIN plan_review_operation o ON o.id=t.operation_id AND o.project_id=t.project_id LEFT JOIN plan_review_relation r ON r.id=o.relation_id AND r.project_id=o.project_id WHERE t.project_id=?"+own+" ORDER BY t.issued_at DESC,t.id LIMIT 100",args.toArray());
        var items=new ArrayList<Map<String,Object>>();
        for(var row:rows){
            boolean current=number(row,"operation_revision")==number(row,"current_revision")&&Objects.equals(row.get("old_actor"),row.get("executing_actor"));
            boolean live=row.get("generation")==null || ("ACTIVE".equals(row.get("relation_state"))&&Objects.equals(row.get("generation"),row.get("relation_generation")));
            boolean available=current&&live&&row.get("consumed_at")==null&&now()<number(row,"expires_at")&&!Set.of("TERMINATED","COMPLETED","SKIPPED").contains(row.get("operation_state"));
            var item=new LinkedHashMap<String,Object>();
            item.put("previewId",row.get("id"));item.put("operationId",row.get("operation_id"));item.put("revision",row.get("operation_revision"));
            item.put("oldActor",row.get("old_actor"));item.put("newActor",row.get("new_actor"));item.put("expiresAt",row.get("expires_at"));
            item.put("credentialToWeb",Boolean.TRUE.equals(row.get("credential_to_web")));
            item.put("approved",row.get("approved_by")!=null);item.put("consumed",row.get("consumed_at")!=null);item.put("stale",!available);
            boolean eligible=false;
            if(available)try{rights(row,(String)row.get("new_actor"));eligible=true;}catch(PlanReviewFailure denied){/* Display a safe disabled reason; never fall back to another account. */}
            if(available&&!eligible)item.put("blockedReason","当前账号权限或服务授权不足，接管尚不可执行");
            item.put("canApprove",available&&eligible&&canApprove&&row.get("approved_by")==null);
            item.put("canTakeover",available&&eligible&&actor.equals(row.get("new_actor"))&&row.get("approved_by")!=null);
            items.add(item);
        }
        return Map.of("items",items);
    }
    public Map<String,Object> resume(String project,String id,long expected,String requestKey,String actor){
        key(requestKey);var operation=lock(project,id,actor);owner(operation,actor);rights(operation,actor);credential(operation,actor);fence(operation);
        var replay=commandReplay(id,requestKey,expected,actor,"RESUME");if(replay!=null)return replay;
        if(Set.of("COMPLETED","SKIPPED").contains(operation.get("state")))return result(operation);
        revision(operation,expected);
        var providers=resumers.stream().filter(provider->provider.supports((String)operation.get("source_kind"),(String)operation.get("operation_type"))).toList();
        if(providers.size()!=1)throw fail("RESUME_HANDLER_UNAVAILABLE",409,"原业务恢复器尚不可用，已成功结果保持不变");
        providers.getFirst().resume(Collections.unmodifiableMap(operation),actor);
        jdbc.update("UPDATE plan_review_operation SET revision=revision+1,safe_error=NULL,update_time=? WHERE id=?",now(),id);
        audit(operation,"RESUME",actor,null);
        var result=result(jdbc.queryForMap("SELECT * FROM plan_review_operation WHERE id=? FOR UPDATE",id));
        saveCommand(id,requestKey,expected,actor,"RESUME",result);return result;
    }
    public Map<String,Object> terminate(String project,String id,long expected,String actor){
        var operation=lock(project,id,actor);owner(operation,actor);rights(operation,actor);credential(operation,actor);
        if("TERMINATED".equals(operation.get("state")))return result(operation);
        revision(operation,expected);
        if(Set.of("COMPLETED","SKIPPED").contains(operation.get("state")))throw fail("OPERATION_FINISHED",409,"已完成或明确跳过的操作不能终止");
        for(var provider:resumers)if(provider.supports((String)operation.get("source_kind"),(String)operation.get("operation_type")))provider.onTerminate(Collections.unmodifiableMap(operation),actor);
        jdbc.update("UPDATE plan_review_operation SET state='TERMINATED',revision=revision+1,update_time=? WHERE id=?",now(),id);
        audit(operation,"TERMINATE",actor,null);
        return result(jdbc.queryForMap("SELECT * FROM plan_review_operation WHERE id=? FOR UPDATE",id));
    }
    public Map<String,Object> takeoverPreview(String project,String id,long expected,String newActor,String actor){
        if(newActor==null||newActor.isBlank()||newActor.length()>50)throw fail("INVALID_ACTOR",400,"请选择有效接管人");
        var operation=lock(project,id,actor);
        if(resumers.stream().noneMatch(provider->provider.supports((String)operation.get("source_kind"),(String)operation.get("operation_type"))))throw fail("RESUME_HANDLER_UNAVAILABLE",409,"原业务恢复器尚不可用，成功结果保留，不会改用其他流程");
        if(((String)operation.get("request_key")).startsWith("import:"))throw fail("RECOVERY_CONTEXT_REQUIRED",409,"导入后处理须连同原任务执行身份一并接管，不能仅接管其创建子步骤");
        boolean groupOnly="COPY_GROUP".equals(operation.get("operation_type"));
        if(!actor.equals(operation.get("executing_actor")))approver(actor,project,groupOnly);
        authorization.requireRead(actor,project,true);if(!groupOnly)authorization.requireRead(actor,project,false);
        revision(operation,expected);fence(operation);rights(operation,newActor);
        if(newActor.equals(operation.get("executing_actor"))&&operation.get("executing_credential_id")==null || Set.of("COMPLETED","SKIPPED").contains(operation.get("state")))throw fail("OPERATION_FINISHED",409,"该操作无需接管");
        String preview=UUID.randomUUID().toString();long issued=now();
        boolean credentialToWeb=operation.get("executing_credential_id")!=null;
        jdbc.update("INSERT INTO plan_review_takeover(id,project_id,operation_id,operation_revision,old_actor,new_actor,requested_by,generation,issued_at,expires_at,credential_to_web) VALUES(?,?,?,?,?,?,?,?,?,?,?)",preview,project,id,expected,operation.get("executing_actor"),newActor,actor,operation.get("generation"),issued,issued+600_000,credentialToWeb);
        return Map.of("previewId",preview,"operationId",id,"revision",expected,"expiresAt",issued+600_000,"approvalRequired",true,"credentialToWeb",credentialToWeb);
    }
    public Map<String,Object> approve(String project,String previewId,boolean acknowledged,String actor){
        if(!acknowledged)throw fail("ACK_REQUIRED",400,"请明确确认接管影响");
        approver(actor,project,true);
        var hint=preview(project,previewId);var operation=lock(project,(String)hint.get("operation_id"),actor);
        var preview=jdbc.queryForMap("SELECT * FROM plan_review_takeover WHERE id=? AND project_id=? FOR UPDATE",previewId,project);
        approver(actor,project,"COPY_GROUP".equals(operation.get("operation_type")));
        validPreview(preview,operation);rights(operation,(String)preview.get("new_actor"));
        if(preview.get("approved_by")!=null && !actor.equals(preview.get("approved_by")))throw fail("APPROVAL_CONFLICT",409,"该审批已有审批者");
        if(actor.equals(preview.get("approved_by")))return Map.of("approvalId",previewId,"expiresAt",preview.get("expires_at"),"replayed",true);
        jdbc.update("UPDATE plan_review_takeover SET approved_by=? WHERE id=?",actor,previewId);
        audit(operation,"TAKEOVER_APPROVED",actor,(String)preview.get("new_actor"));
        return Map.of("approvalId",previewId,"expiresAt",preview.get("expires_at"));
    }
    public Map<String,Object> takeover(String project,String approvalId,long expected,String requestKey,String actor){
        key(requestKey);var hint=preview(project,approvalId);var operation=lock(project,(String)hint.get("operation_id"),actor);
        var preview=jdbc.queryForMap("SELECT * FROM plan_review_takeover WHERE id=? AND project_id=? FOR UPDATE",approvalId,project);
        if(!actor.equals(preview.get("new_actor")))throw fail("RESOURCE_NOT_ACCESSIBLE",404,"审批不可访问");
        rights(operation,actor);fence(operation);
        if(preview.get("consumed_at")!=null){
            if(!requestKey.equals(preview.get("request_key")) || expected!=number(preview,"operation_revision"))throw fail("APPROVAL_CONSUMED",409,"审批已被消费");
            return Map.of("operationId",operation.get("id"),"revision",preview.get("result_revision"),"replayed",true);
        }
        revision(operation,expected);validPreview(preview,operation);
        if(preview.get("approved_by")==null)throw fail("APPROVAL_REQUIRED",403,"接管尚未获得项目负责人批准");
        approver((String)preview.get("approved_by"),project,"COPY_GROUP".equals(operation.get("operation_type")));
        jdbc.update("UPDATE plan_review_operation SET executing_actor=?,executing_credential_id=NULL,approver=?,revision=revision+1,update_time=? WHERE id=?",actor,preview.get("approved_by"),now(),operation.get("id"));
        var providers=resumers.stream().filter(provider->provider.supports((String)operation.get("source_kind"),(String)operation.get("operation_type"))).toList();
        if(providers.size()!=1)throw fail("RESUME_HANDLER_UNAVAILABLE",409,"原业务恢复器不可用，接管未生效");
        providers.getFirst().onTakeover(Collections.unmodifiableMap(operation),actor,(String)preview.get("approved_by"));
        jdbc.update("UPDATE plan_review_takeover SET consumed_at=?,request_key=?,result_revision=? WHERE id=? AND consumed_at IS NULL",now(),requestKey,expected+1,approvalId);
        audit(operation,"TAKEOVER",actor,(String)preview.get("approved_by"));
        return Map.of("operationId",operation.get("id"),"revision",expected+1,"replayed",false);
    }
    private Map<String,Object> preview(String project,String id){
        var rows=jdbc.queryForList("SELECT * FROM plan_review_takeover WHERE id=? AND project_id=?",id,project);
        if(rows.size()!=1)throw fail("RESOURCE_NOT_ACCESSIBLE",404,"预览不可访问");return rows.getFirst();
    }
    private void validPreview(Map<String,Object> preview,Map<String,Object> operation){
        if(Set.of("COMPLETED","SKIPPED").contains(operation.get("state")))throw fail("OPERATION_FINISHED",409,"原操作已完成或明确跳过，审批不能继续改变执行身份");
        if(now()>=number(preview,"expires_at"))throw fail("PREVIEW_EXPIRED",410,"接管预览已过期");
        revision(operation,number(preview,"operation_revision"));fence(operation);
        if(!Objects.equals(preview.get("generation"),operation.get("generation")) || !Objects.equals(preview.get("old_actor"),operation.get("executing_actor")))throw fail("PREVIEW_STALE",409,"操作或执行身份已变化");
    }
    private Map<String,Object> result(Map<String,Object> operation){
        var result=new LinkedHashMap<String,Object>(Map.of("operationId",operation.get("id"),"stage",operation.get("stage"),"state",operation.get("state"),"revision",operation.get("revision")));
        if("CREATE_REVIEW".equals(operation.get("operation_type"))&&operation.get("result_json")!=null){
            var stored=io.metersphere.sdk.util.JSON.parseObject((String)operation.get("result_json"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
            if(stored.get("newReviewPreview") instanceof Map<?,?> preview)result.put("newReviewPreview",preview);
        }
        return result;
    }
    private Map<String,Object> commandReplay(String id,String key,long expected,String actor,String command){
        var rows=jdbc.queryForList("SELECT * FROM plan_review_operation_command WHERE operation_id=? AND request_key=? FOR UPDATE",id,key);
        if(rows.isEmpty())return null;var row=rows.getFirst();
        if(!actor.equals(row.get("actor"))||!command.equals(row.get("command"))||expected!=number(row,"expected_revision"))throw fail("REQUEST_KEY_CONFLICT",409,"同一请求标识不能用于不同的恢复命令");
        return io.metersphere.sdk.util.JSON.parseObject((String)row.get("result_json"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
    }
    private void saveCommand(String id,String key,long expected,String actor,String command,Map<String,Object> result){
        jdbc.update("INSERT INTO plan_review_operation_command(operation_id,request_key,actor,command,expected_revision,result_json,create_time) VALUES(?,?,?,?,?,?,?)",id,key,actor,command,expected,io.metersphere.sdk.util.JSON.toJSONString(result),now());
    }
    private void audit(Map<String,Object> operation,String action,String actor,String reason){
        jdbc.update("INSERT INTO plan_review_audit(id,project_id,relation_id,generation,operation_id,action,actor,result,safe_reason,create_time) VALUES(?,?,?,?,?,?,?,'SUCCESS',?,?)",UUID.randomUUID().toString(),operation.get("project_id"),operation.get("relation_id"),operation.get("generation"),operation.get("id"),action,actor,reason,now());
    }
    private static void key(String value){if(value==null||value.isBlank()||value.length()>128)throw fail("REQUEST_KEY_REQUIRED",400,"请保留有效请求标识");}
    private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static long now(){return System.currentTimeMillis();}
    private static PlanReviewFailure fail(String code,int status,String message){return new PlanReviewFailure(code,status,message);}
}
