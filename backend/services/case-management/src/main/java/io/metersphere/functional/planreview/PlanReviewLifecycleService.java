package io.metersphere.functional.planreview;

import io.metersphere.sdk.util.JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Restoring local editability never grants or performs membership reconciliation. */
@Service
@Transactional(rollbackFor = Exception.class)
public class PlanReviewLifecycleService {
    private final JdbcTemplate jdbc;
    private final PlanReviewPersistence persistence;
    private final PlanReviewAuthorization authorization;
    private final PlanReviewPlanStateResolver states;
    private final PlanReviewCompletionGuard completion;
    public PlanReviewLifecycleService(JdbcTemplate jdbc, PlanReviewPersistence persistence, PlanReviewAuthorization authorization, PlanReviewPlanStateResolver states) {
        this.jdbc = jdbc; this.persistence = persistence; this.authorization = authorization; this.states = states;
        this.completion = new PlanReviewCompletionGuard(jdbc,states);
    }
    private record Context(Map<String,Object> object, Map<String,Object> relation, String project, boolean plan) { }
    private Context lock(String side, String id, String actor, String action) {
        if (side == null || !Set.of("plan", "review").contains(side)) throw fail("INVALID_SIDE",400,"对象类型无效");
        boolean plan = side.equals("plan");
        String table = plan ? "test_plan" : "case_review";
        var rows = jdbc.queryForList("SELECT * FROM " + table + " WHERE id=?" + (plan ? " AND type='TEST_PLAN'" : ""),id);
        if (rows.size()!=1) throw fail("RESOURCE_NOT_ACCESSIBLE",404,"对象不可访问");
        String project = (String) rows.getFirst().get("project_id");
        var access = authorization.current(actor,project,true);
        var permissions = access.permissions();
        if (!permissions.containsAll(Set.of(plan ? PlanReviewPolicy.Permission.PLAN_READ : PlanReviewPolicy.Permission.REVIEW_READ,
                plan ? PlanReviewPolicy.Permission.PLAN_UPDATE : PlanReviewPolicy.Permission.REVIEW_UPDATE))) throw fail("PERMISSION_REQUIRED",403,"缺少本侧读取与更新权限");
        if (action.startsWith("RESTORE") && !permissions.contains(PlanReviewPolicy.Permission.RESTORE_EDIT)) throw fail("PERMISSION_REQUIRED",403,"缺少恢复本侧编辑权限");
        if (plan) persistence.lockSource(id); else persistence.lockReview(id);
        var object = jdbc.queryForMap("SELECT * FROM " + table + " WHERE id=? FOR UPDATE",id);
        if (plan) object = states.resolve(object);
        var relations = jdbc.queryForList("SELECT * FROM plan_review_relation WHERE project_id=? AND " + (plan ? "active_plan_id" : "active_review_id") + "=? FOR UPDATE",project,id);
        var relation = relations.isEmpty() ? null : relations.getFirst();
        if (relation != null && action.startsWith("RESTORE")) authorization.require(plan ? PlanReviewPolicy.Action.RESTORE_PLAN : PlanReviewPolicy.Action.RESTORE_REVIEW,actor,project);
        return new Context(object,relation,project,plan);
    }
    public Map<String,Object> preview(String side, String id, String action, String actor) {
        if (action == null || !Set.of("COMPLETE","ARCHIVE","RESTORE_ARCHIVE","RESTORE_EDIT").contains(action)) throw fail("INVALID_ACTION",400,"状态操作无效");
        var context=lock(side,id,actor,action);
        validateState(context,action);
        if(context.plan)persistence.sourceChanged(id);else if(context.relation!=null)persistence.sourceChanged((String)context.relation.get("plan_id"));
        var relation = refresh(context);
        long source=relation==null?0:number(relation,"source_version");
        String generation=relation==null?null:(String)relation.get("generation");
        String fingerprint=fingerprint(context);
        long now=System.currentTimeMillis(); String preview=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO plan_review_lifecycle_preview(id,project_id,object_side,object_id,actor,action,generation,object_revision,object_state,source_version,target_fingerprint,issued_at,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                preview,context.project,side,id,actor,action,generation,number(context.object,"update_time"),context.object.get("status"),source,fingerprint,now,now+600_000);
        if(context.plan)jdbc.update("UPDATE plan_review_lifecycle_preview SET source_execution_revision=? WHERE id=?",completion.executionRevision(id),preview);
        return Map.of("previewId",preview,"action",action,"objectState",context.object.get("status"),"sourceVersion",source,"appliedVersion",relation==null?0:number(relation,"applied_version"),"expiresAt",now+600_000,
                "message",action.startsWith("RESTORE")?"只恢复本侧状态，不修改成员，也不授权追平。双方可编辑后仍需重新预览并确认同步。":"将保存当前范围、统计和差异快照。归档后的新增变化不会覆盖冻结快照。");
    }
    public Map<String,Object> confirm(String previewId, boolean acknowledged, String actor) {
        if (!acknowledged) throw fail("ACK_REQUIRED",400,"请先确认本侧状态及冻结影响");
        var rows=jdbc.queryForList("SELECT * FROM plan_review_lifecycle_preview WHERE id=? AND actor=?",previewId,actor);
        if(rows.size()!=1)throw fail("RESOURCE_NOT_ACCESSIBLE",404,"预览不可访问");
        var preview=rows.getFirst(); String side=(String)preview.get("object_side"),id=(String)preview.get("object_id"),action=(String)preview.get("action");
        var context=lock(side,id,actor,action);
        preview=jdbc.queryForMap("SELECT * FROM plan_review_lifecycle_preview WHERE id=? FOR UPDATE",previewId);
        if (preview.get("consumed_at")!=null) return Map.of("objectState",context.object.get("status"),"replayed",true,"membershipChanged",false);
        if(System.currentTimeMillis()>=number(preview,"expires_at"))throw fail("PREVIEW_EXPIRED",410,"预览已过期，请重新预览");
        if(context.plan)persistence.sourceChanged(id);else if(context.relation!=null)persistence.sourceChanged((String)context.relation.get("plan_id"));
        var relation=refresh(context);
        if(number(context.object,"update_time")!=number(preview,"object_revision") || !Objects.equals(context.object.get("status"),preview.get("object_state"))
                || !Objects.equals(relation==null?null:relation.get("generation"),preview.get("generation"))
                || (relation==null?0:number(relation,"source_version"))!=number(preview,"source_version") || !fingerprint(context).equals(preview.get("target_fingerprint"))) throw fail("PREVIEW_STALE",409,"状态、成员或意见已变化，请重新预览");
        if(context.plan && !Objects.equals(preview.get("source_execution_revision"),completion.executionRevision(id)))throw fail("PREVIEW_STALE",409,"执行结果或成员范围已变化，请重新预览");
        validateState(context,action);
        if("COMPLETE".equals(action) && relation!=null && (!"SYNCED".equals(relation.get("sync_state")) || number(relation,"source_version")!=number(relation,"applied_version"))) throw fail("SYNC_NOT_CONVERGED",409,"同步尚未追平或存在失败，不能完成；可查看差异后处理");
        if("COMPLETE".equals(action) && !context.plan && jdbc.queryForObject("SELECT COUNT(*) FROM case_review_functional_case WHERE review_id=? AND status NOT IN ('PASS','UN_PASS')",Long.class,id)>0) throw fail("REVIEW_NOT_FINISHED",409,"仍有未完成评审的当前用例，不能完成");
        String target=switch(action){case "COMPLETE","RESTORE_ARCHIVE"->"COMPLETED";case "ARCHIVE"->"ARCHIVED";default->"UNDERWAY";};
        if(relation!=null && !action.startsWith("RESTORE")) {
            persistence.freeze(relation,side,id,target,fingerprint(context),actor,states.statisticsSnapshot((String)relation.get("plan_id")));
        }
        if(relation==null && context.plan && !action.startsWith("RESTORE"))persistence.freezeIndependentPlan(id,target,actor,states.statisticsSnapshot(id));
        if(relation==null && !context.plan && !action.startsWith("RESTORE"))persistence.freezeIndependentReview(id,target,actor);
        String storageTarget=context.plan && "RESTORE_EDIT".equals(action)?"NOT_ARCHIVED":target;
        jdbc.update("UPDATE "+(context.plan?"test_plan":"case_review")+" SET status=?,update_time=? WHERE id=?",storageTarget,System.currentTimeMillis(),id);
        if(relation!=null && action.startsWith("RESTORE"))jdbc.update("UPDATE plan_review_relation SET sync_state='RECONFIRM_REQUIRED',update_time=? WHERE id=? AND generation=? AND state='ACTIVE'",System.currentTimeMillis(),relation.get("id"),relation.get("generation"));
        jdbc.update("UPDATE plan_review_lifecycle_preview SET consumed_at=? WHERE id=?",System.currentTimeMillis(),previewId);
        return Map.of("objectState",target,"membershipChanged",false,"syncConfirmationRequired",action.startsWith("RESTORE"));
    }

    /** Native execution still saves results. This only persists a legitimately derived, converged completion. */
    public void observePlanCompletion(String plan){
        persistence.lockSource(plan);
        var raw=jdbc.queryForMap("SELECT * FROM test_plan WHERE id=? FOR UPDATE",plan);
        if(!"NOT_ARCHIVED".equals(raw.get("status")) || completion.reopenUnchanged(plan) || !"COMPLETED".equals(states.completionState(plan)))return;
        var rows=jdbc.queryForList("SELECT * FROM plan_review_relation WHERE active_plan_id=? AND state='ACTIVE' FOR UPDATE",plan);
        if(rows.isEmpty()){
            if(!completion.reopened(plan))return;
            persistence.sourceChanged(plan);
            var snapshot=new LinkedHashMap<>(states.statisticsSnapshot(plan));snapshot.put("status","COMPLETED");snapshot.put("completionBlockedByReview",false);snapshot.put("completionSnapshotPending",false);
            persistence.freezeIndependentPlan(plan,"COMPLETED","plan-review-completion",snapshot);
            jdbc.update("UPDATE test_plan SET status='COMPLETED',update_time=? WHERE id=? AND status='NOT_ARCHIVED'",System.currentTimeMillis(),plan);
            return;
        }
        if(rows.size()!=1)return;
        persistence.sourceChanged(plan);
        if(!completion.permit(plan))return;
        var relation=jdbc.queryForMap("SELECT * FROM plan_review_relation WHERE id=? FOR UPDATE",rows.getFirst().get("id"));
        var context=new Context(raw,relation,(String)raw.get("project_id"),true);
        var snapshot=new LinkedHashMap<>(states.statisticsSnapshot(plan));snapshot.put("status","COMPLETED");snapshot.put("completionBlockedByReview",false);snapshot.put("completionSnapshotPending",false);
        persistence.freeze(relation,"plan",plan,"COMPLETED",fingerprint(context),"plan-review-completion",snapshot);
        jdbc.update("UPDATE test_plan SET status='COMPLETED',update_time=? WHERE id=? AND status='NOT_ARCHIVED'",System.currentTimeMillis(),plan);
    }

    /** Original archive entry may proceed without a new dialog only when no unresolved membership exists. */
    public void archiveHealthyPlan(String plan,String actor){
        var context=lock("plan",plan,actor,"ARCHIVE");
        if(context.relation==null){persistence.sourceChanged(plan);persistence.freezeIndependentPlan(plan,"ARCHIVED",actor,states.statisticsSnapshot(plan));return;}
        persistence.sourceChanged(plan);
        if(!completion.permit(plan))throw fail("ARCHIVE_PREVIEW_REQUIRED",409,"存在未追平差异或失败，请从关联卡预览并确认冻结影响后归档");
        var current=refresh(context);
        persistence.freeze(current,"plan",plan,"ARCHIVED",fingerprint(context),actor,states.statisticsSnapshot(plan));
    }

    public Map<String,Object> frozen(String side,String id,String actor){
        return frozen(side,id,actor,1,20);
    }
    public Map<String,Object> frozen(String side,String id,String actor,int page,int size){
        if(page<1 || size<1 || size>100)throw fail("INVALID_PAGE",400,"分页参数无效");
        if(side==null || !Set.of("plan","review").contains(side))throw fail("INVALID_SIDE",400,"对象类型无效");
        boolean plan=side.equals("plan");
        var objects=jdbc.queryForList("SELECT project_id FROM "+(plan?"test_plan":"case_review")+" WHERE id=?",String.class,id);
        if(objects.size()!=1)throw fail("RESOURCE_NOT_ACCESSIBLE",404,"对象不可访问");
        authorization.requireRead(actor,objects.getFirst(),plan);
        var frame=new PlanReviewFrozenReader(jdbc).frame(side,id);
        if(frame.isEmpty())return Map.of("available",false);
        var result=new LinkedHashMap<String,Object>();result.put("available",true);result.put("frozenAt",frame.get("create_time"));result.put("objectState",frame.get("object_state"));
        var data=(Map<?,?>)frame.get("data");
        if(plan){var statistics=new LinkedHashMap<String,Object>();if(data.get("sourceStatistics") instanceof Map<?,?> stats)for(String key:PlanReviewFrozenReader.STATISTIC_FIELDS)if(stats.get(key)!=null)statistics.put(key,stats.get(key));result.put("statistics",statistics);result.put("members",data.get("source"));}
        else {var statistics=new LinkedHashMap<String,Object>();if(data.get("review") instanceof Map<?,?> review)for(String key:List.of("id","name","num","status","case_count","pass_rate","review_pass_rule"))if(review.get(key)!=null)statistics.put(key,review.get(key));result.put("statistics",statistics);result.put("members",data.get("members"));}
        var access=authorization.current(actor,objects.getFirst(),false);
        if(access.permissions().containsAll(Set.of(PlanReviewPolicy.Permission.PLAN_READ,PlanReviewPolicy.Permission.REVIEW_READ))){result.put("sourceVersion",frame.get("source_version"));result.put("appliedVersion",frame.get("applied_version"));}
        Object members=result.remove("members"); var scope=members instanceof List<?> list?list:List.of();
        int from=(int)Math.min((page-1L)*size,scope.size());
        result.put("members",scope.subList(from,Math.min(from+size,scope.size())));result.put("total",scope.size());result.put("page",page);result.put("size",size);
        return result;
    }
    private Map<String,Object> refresh(Context context){return context.relation==null?null:jdbc.queryForMap("SELECT * FROM plan_review_relation WHERE id=? FOR UPDATE",context.relation.get("id"));}
    private String fingerprint(Context context) {
        if(context.plan && context.relation==null)return completion.executionRevision((String)context.object.get("id"));
        String review=context.relation==null?(String)context.object.get("id"):(String)context.relation.get("review_id");
        return new PlanReviewFingerprintReader(jdbc).read(review, PlanReviewFingerprintReader.ReadMode.LOCKED, PlanReviewFingerprintReader.Format.COMPLETION_V1);
    }
    private void validateState(Context context,String action){
        var state=PlanReviewPolicy.State.parse((String)context.object.get("status"));
        boolean valid=switch(action){case "RESTORE_ARCHIVE"->state==PlanReviewPolicy.State.ARCHIVED;case "RESTORE_EDIT"->state==PlanReviewPolicy.State.COMPLETED;
            case "COMPLETE"->state.editable();case "ARCHIVE"->state.editable()||state==PlanReviewPolicy.State.COMPLETED;default->false;};
        if(!valid)throw fail("OBJECT_STATE_CONFLICT",409,"当前状态不允许该操作；归档须先恢复到完成，再单独恢复编辑");
    }
    private static long number(Map<String,Object> row,String field){return row.get(field) instanceof Number n?n.longValue():0;}
    private static PlanReviewFailure fail(String code,int status,String message){return new PlanReviewFailure(code,status,message);}
}
