package io.metersphere.functional.planreview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Durable fault cohorts, separately budgeted outbox and explicit notification-only recovery. */
@Service
public class PlanReviewNoticeService {
    private final JdbcTemplate jdbc;
    private final PlanReviewAuthorization authorization;
    private final PlanReviewNoticeChannel channel;
    private final TransactionTemplate tx;
    private final PlanReviewPlanStateResolver states;
    private String cursor="";
    public PlanReviewNoticeService(JdbcTemplate jdbc,PlanReviewAuthorization authorization,PlanReviewNoticeChannel channel,PlatformTransactionManager manager){
        this(jdbc,authorization,channel,manager,new PlanReviewPlanStateResolver(List.of()));
    }
    @org.springframework.beans.factory.annotation.Autowired
    public PlanReviewNoticeService(JdbcTemplate jdbc,PlanReviewAuthorization authorization,PlanReviewNoticeChannel channel,PlatformTransactionManager manager,PlanReviewPlanStateResolver states){
        this.jdbc=jdbc;this.authorization=authorization;this.channel=channel;tx=new TransactionTemplate(manager);tx.setTimeout(30);
        this.states=states;
    }
    @Scheduled(fixedDelay=1000,initialDelay=30000)
    public void drain(){
        var relations=jdbc.queryForList("SELECT id,project_id,generation FROM plan_review_relation WHERE protocol_version=2 AND id>? ORDER BY id LIMIT 100",cursor);
        if(relations.isEmpty())cursor="";
        for(var relation:relations){tx.executeWithoutResult(status->collect((String)relation.get("project_id"),(String)relation.get("id"),(String)relation.get("generation")));cursor=(String)relation.get("id");}
        var notices=jdbc.queryForList("SELECT id,project_id,relation_id,generation,retry_round,attempts FROM plan_review_notice WHERE state IN ('PENDING','WAITING_RETRY') AND (next_retry_at IS NULL OR next_retry_at<=?) ORDER BY persisted_at,id LIMIT 50",now());
        for(var notice:notices){
            try{tx.executeWithoutResult(status->deliver(notice));}
            catch(RuntimeException failed){tx.executeWithoutResult(status->failed(notice));}
        }
    }
    private Map<String,Object> relation(String project,String id,String generation){
        jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE",project);
        var rows=jdbc.queryForList("SELECT * FROM plan_review_relation WHERE id=? AND project_id=? AND generation=? FOR UPDATE",id,project,generation);
        if(rows.size()!=1)throw denied();return rows.getFirst();
    }
    void collect(String project,String id,String generation){
        var relation=relation(project,id,generation);
        if(!"ACTIVE".equals(relation.get("state"))){jdbc.update("UPDATE plan_review_fault SET ended_at=COALESCE(ended_at,?),active_reason=NULL WHERE relation_id=? AND generation=? AND recovered_at IS NULL",now(),id,generation);return;}
        boolean grant=jdbc.queryForList("SELECT enabled FROM plan_review_grant WHERE project_id=?",Boolean.class,project).stream().findFirst().orElse(false);
        var plans=jdbc.queryForList("SELECT p.* FROM test_plan p JOIN project pr ON pr.id=p.project_id AND pr.enable=1 AND pr.deleted=0 WHERE p.id=? AND p.project_id=? FOR UPDATE",relation.get("plan_id"),project);
        boolean editable=plans.size()==1&&PlanReviewPolicy.State.parse((String)states.resolve(plans.getFirst()).get("status")).editable()
                &&jdbc.queryForObject("SELECT COUNT(*) FROM case_review WHERE id=? AND project_id=? AND status IN ('PREPARED','UNDERWAY')",Integer.class,relation.get("review_id"),project)==1;
        var reasons=new TreeSet<String>();
        if(!grant)reasons.add("GRANT_REVOKED");
        if("FAILED_MANUAL".equals(relation.get("sync_state")))reasons.add("RETRY_EXHAUSTED");
        if(grant&&editable&&number(relation,"source_version")>number(relation,"applied_version")&&now()-number(relation,"source_changed_at")>30000)reasons.add("DELAYED");
        for(String reason:reasons){
            if(jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_fault WHERE relation_id=? AND generation=? AND active_reason=?",Integer.class,id,generation,reason)>0)continue;
            long round=number(relation,"fault_round")+1;relation.put("fault_round",round);
            jdbc.update("UPDATE plan_review_relation SET fault_round=? WHERE id=? AND generation=?",round,id,generation);
            String fault=uuid();long detected=now();
            jdbc.update("INSERT INTO plan_review_fault(id,project_id,relation_id,generation,reason,fault_round,active_reason,detected_at) VALUES(?,?,?,?,?,?,?,?)",fault,project,id,generation,reason,round,reason,detected);
            for(String recipient:channel.recipients(relation,reason))intent(project,id,generation,round,reason,recipient,"FAULT",detected,null);
        }
        // A restored channel/grant alone is insufficient: the latest approved business set must be applied.
        if(grant&&"SYNCED".equals(relation.get("sync_state"))&&number(relation,"source_version")==number(relation,"applied_version")&&!truth(relation.get("approval_pending"))){
            for(var fault:jdbc.queryForList("SELECT * FROM plan_review_fault WHERE relation_id=? AND generation=? AND active_reason IS NOT NULL FOR UPDATE",id,generation)){
                long recovered=now();
                jdbc.update("UPDATE plan_review_fault SET recovered_at=?,active_reason=NULL WHERE id=?",recovered,fault.get("id"));
                jdbc.update("UPDATE plan_review_notice SET recovered_at=? WHERE relation_id=? AND generation=? AND fault_round=?",recovered,id,generation,fault.get("fault_round"));
                for(String recipient:jdbc.queryForList("SELECT recipient_id FROM plan_review_notice WHERE relation_id=? AND generation=? AND fault_round=? AND type='FAULT'",String.class,id,generation,fault.get("fault_round")))intent(project,id,generation,number(fault,"fault_round"),(String)fault.get("reason"),recipient,"RECOVERY",number(fault,"detected_at"),recovered);
            }
        }
    }
    private void intent(String project,String relation,String generation,long round,String reason,String recipient,String type,long detected,Long recovered){
        String eligibility=channel.eligibility(project,recipient);
        jdbc.update("INSERT IGNORE INTO plan_review_notice(id,project_id,relation_id,generation,fault_round,reason,type,recipient_id,state,detected_at,persisted_at,recovered_at,eligibility) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",uuid(),project,relation,generation,round,reason,type,recipient,"CURRENT".equals(eligibility)?"PENDING":"INELIGIBLE",detected,now(),recovered,eligibility);
    }
    void deliver(Map<String,Object> selected){
        var relation=relation((String)selected.get("project_id"),(String)selected.get("relation_id"),(String)selected.get("generation"));
        var notice=jdbc.queryForMap("SELECT * FROM plan_review_notice WHERE id=? AND project_id=? FOR UPDATE",selected.get("id"),selected.get("project_id"));
        if(!Set.of("PENDING","WAITING_RETRY").contains(notice.get("state"))||notice.get("readable_at")!=null||notice.get("next_retry_at") instanceof Number due&&due.longValue()>now())return;
        String eligibility=channel.eligibility((String)notice.get("project_id"),(String)notice.get("recipient_id"));
        if(!"CURRENT".equals(eligibility)){jdbc.update("UPDATE plan_review_notice SET state='INELIGIBLE',eligibility=?,next_retry_at=NULL WHERE id=?",eligibility,notice.get("id"));return;}
        if("RECOVERY".equals(notice.get("type"))&&jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_notice WHERE relation_id=? AND generation=? AND fault_round=? AND recipient_id=? AND type='FAULT' AND readable_at IS NOT NULL",Integer.class,notice.get("relation_id"),notice.get("generation"),notice.get("fault_round"),notice.get("recipient_id"))!=1){
            jdbc.update("UPDATE plan_review_notice SET next_retry_at=? WHERE id=?",now()+1000,notice.get("id"));return;
        }
        long inbox=channel.publish((String)notice.get("recipient_id"),(String)notice.get("id"),number(notice,"detected_at"),notice.get("recovered_at")!=null,!"ACTIVE".equals(relation.get("state")),(String)notice.get("type"));
        jdbc.update("UPDATE plan_review_notice SET state='READABLE',readable_at=?,last_finished_at=?,next_retry_at=NULL,inbox_id=?,eligibility='CURRENT',safe_error=NULL WHERE id=?",now(),now(),inbox,notice.get("id"));
    }
    private void failed(Map<String,Object> selected){
        relation((String)selected.get("project_id"),(String)selected.get("relation_id"),(String)selected.get("generation"));
        var notice=jdbc.queryForMap("SELECT * FROM plan_review_notice WHERE id=? FOR UPDATE",selected.get("id"));
        if(!Set.of("PENDING","WAITING_RETRY").contains(notice.get("state"))||notice.get("readable_at")!=null)return;
        if(number(notice,"retry_round")!=number(selected,"retry_round")||number(notice,"attempts")!=number(selected,"attempts"))return;
        int attempts=(int)number(notice,"attempts")+1;long finished=now();
        jdbc.update("UPDATE plan_review_notice SET attempts=?,state=?,last_finished_at=?,next_retry_at=?,safe_error=? WHERE id=?",attempts,attempts>=4?"FAILED_MANUAL":"WAITING_RETRY",finished,attempts>=4?null:finished+new long[]{10000,30000,60000}[attempts-1],"站内通知暂不可读；成员同步结果未改变",notice.get("id"));
    }
    private PlanReviewPolicy.Access read(String project,String actor){
        var access=authorization.current(actor,project,false);
        if(!access.permissions().contains(PlanReviewPolicy.Permission.NOTICE_RECOVER)&&!access.permissions().contains(PlanReviewPolicy.Permission.PLAN_READ)&&!access.permissions().contains(PlanReviewPolicy.Permission.REVIEW_READ))throw denied();return access;
    }
    public Map<String,Object> list(String project,String relation,String actor){
        var access=read(project,actor);
        boolean manager=access.permissions().contains(PlanReviewPolicy.Permission.NOTICE_RECOVER);
        var rows=jdbc.queryForList("SELECT id,reason,fault_round,detected_at,recovered_at,ended_at FROM plan_review_fault WHERE project_id=? AND relation_id=? ORDER BY detected_at DESC LIMIT 100",project,relation);
        var items=new ArrayList<Map<String,Object>>();
        for(var fault:rows){
            var item=new LinkedHashMap<>(fault);
            var notices=jdbc.queryForList("SELECT type,state,attempts,retry_round,next_retry_at,detected_at,persisted_at,readable_at,recovered_at,last_finished_at,safe_error,eligibility FROM plan_review_notice WHERE project_id=? AND relation_id=? AND fault_round=? ORDER BY type,recipient_id",project,relation,fault.get("fault_round"));
            item.put("deliveries",notices);item.put("noEligibleRecipients",notices.stream().noneMatch(n->"CURRENT".equals(n.get("eligibility"))));items.add(item);
        }
        return Map.of("items",items,"canRecover",manager);
    }
    public Map<String,Object> listObject(String project,String side,String id,String actor){
        if(!Set.of("plan","review").contains(side))throw denied();
        authorization.requireRead(actor,project,"plan".equals(side));
        String table="plan".equals(side)?"test_plan":"case_review",column="plan".equals(side)?"plan_id":"review_id";
        if(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=? AND project_id=?",Integer.class,id,project)!=1)throw denied();
        var items=new ArrayList<Object>();boolean canRecover=false;
        for(String relation:jdbc.queryForList("SELECT id FROM plan_review_relation WHERE project_id=? AND "+column+"=? ORDER BY create_time DESC LIMIT 100",String.class,project,id)){
            var page=list(project,relation,actor);items.addAll((List<?>)page.get("items"));canRecover=Boolean.TRUE.equals(page.get("canRecover"));
        }
        return Map.of("items",items,"canRecover",canRecover);
    }
    private Map<String,Object> fault(String project,String id){
        var rows=jdbc.queryForList("SELECT * FROM plan_review_fault WHERE id=? AND project_id=?",id,project);if(rows.size()!=1)throw denied();
        var initial=rows.getFirst();relation(project,(String)initial.get("relation_id"),(String)initial.get("generation"));
        return jdbc.queryForMap("SELECT * FROM plan_review_fault WHERE id=? AND project_id=? FOR UPDATE",id,project);
    }
    private void recoverPermission(String project,String actor){if(!authorization.current(actor,project,true).permissions().contains(PlanReviewPolicy.Permission.NOTICE_RECOVER))throw new PlanReviewFailure("PERMISSION_REQUIRED",403,"缺少当前项目通知恢复授权");}
    private Map<String,Object> recoveryScope(Map<String,Object> fault){
        var candidates=new TreeSet<>(jdbc.queryForList("SELECT recipient_id FROM plan_review_notice WHERE relation_id=? AND generation=? AND fault_round=? AND type='FAULT'",String.class,fault.get("relation_id"),fault.get("generation"),fault.get("fault_round")));
        candidates.addAll(channel.responsibles((String)fault.get("project_id"),(String)fault.get("reason")));
        var eligibility=new TreeMap<String,String>();for(String candidate:candidates)eligibility.put(candidate,channel.eligibility((String)fault.get("project_id"),candidate));
        var states=jdbc.queryForList("SELECT id,state,attempts,retry_round,readable_at FROM plan_review_notice WHERE relation_id=? AND generation=? AND fault_round=? ORDER BY id",fault.get("relation_id"),fault.get("generation"),fault.get("fault_round"));
        return Map.of("eligibility",eligibility,"states",states,"recovered",fault.get("recovered_at")==null?0:fault.get("recovered_at"),"ended",fault.get("ended_at")==null?0:fault.get("ended_at"));
    }
    @Transactional
    public Map<String,Object> preview(String project,String faultId,String actor){
        var fault=fault(project,faultId);recoverPermission(project,actor);var scope=recoveryScope(fault);
        String id=uuid();jdbc.update("INSERT INTO plan_review_notice_preview(id,project_id,fault_id,actor,fingerprint,expires_at) VALUES(?,?,?,?,?,?)",id,project,faultId,actor,hash(scope),now()+600000);
        @SuppressWarnings("unchecked") var eligible=(Map<String,String>)scope.get("eligibility");
        return Map.of("previewId",id,"eligibleCount",eligible.values().stream().filter("CURRENT"::equals).count(),"ineligibleCount",eligible.values().stream().filter(value->!"CURRENT".equals(value)).count(),"expiresAt",now()+600000);
    }
    @Transactional
    public Map<String,Object> confirm(String project,String previewId,boolean acknowledged,String actor){
        // Read identity, then take locks in grant/relation/fault/preview order.
        var previews=jdbc.queryForList("SELECT fault_id FROM plan_review_notice_preview WHERE id=? AND project_id=? AND actor=?",previewId,project,actor);if(previews.size()!=1)throw denied();
        var fault=fault(project,(String)previews.getFirst().get("fault_id"));recoverPermission(project,actor);
        var preview=jdbc.queryForMap("SELECT * FROM plan_review_notice_preview WHERE id=? FOR UPDATE",previewId);
        if(preview.get("consumed_at")!=null)return Map.of("replayed",true);
        if(!acknowledged||number(preview,"expires_at")<now())throw new PlanReviewFailure("PREVIEW_REQUIRED",409,"请重新预览并明确确认通知补投");
        var scope=recoveryScope(fault);if(!hash(scope).equals(preview.get("fingerprint")))throw new PlanReviewFailure("PREVIEW_STALE",409,"收件资格或投递状态已变化，请重新预览");
        @SuppressWarnings("unchecked") var eligibility=(Map<String,String>)scope.get("eligibility");
        for(var entry:eligibility.entrySet()){
            if(!"CURRENT".equals(entry.getValue()))continue;
            intent(project,(String)fault.get("relation_id"),(String)fault.get("generation"),number(fault,"fault_round"),(String)fault.get("reason"),entry.getKey(),"FAULT",number(fault,"detected_at"),(Long)fault.get("recovered_at"));
            if(fault.get("recovered_at")!=null)intent(project,(String)fault.get("relation_id"),(String)fault.get("generation"),number(fault,"fault_round"),(String)fault.get("reason"),entry.getKey(),"RECOVERY",number(fault,"detected_at"),number(fault,"recovered_at"));
            jdbc.update("UPDATE plan_review_notice SET state='PENDING',attempts=0,retry_round=retry_round+1,next_retry_at=NULL,eligibility='CURRENT',safe_error=NULL WHERE project_id=? AND relation_id=? AND generation=? AND fault_round=? AND recipient_id=? AND readable_at IS NULL AND state IN ('FAILED_MANUAL','INELIGIBLE')",project,fault.get("relation_id"),fault.get("generation"),fault.get("fault_round"),entry.getKey());
        }
        jdbc.update("UPDATE plan_review_notice_preview SET consumed_at=? WHERE id=?",now(),previewId);
        int pending=jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_notice WHERE project_id=? AND relation_id=? AND generation=? AND fault_round=? AND readable_at IS NULL AND state IN ('PENDING','WAITING_RETRY')",Integer.class,project,fault.get("relation_id"),fault.get("generation"),fault.get("fault_round"));
        String state=pending>0?"PENDING":eligibility.values().stream().noneMatch("CURRENT"::equals)?"NO_ELIGIBLE_RECIPIENTS":"NO_UNDELIVERED";
        jdbc.update("INSERT INTO plan_review_audit(id,project_id,relation_id,generation,action,actor,result,create_time) VALUES(?,?,?,?,'NOTICE_RECOVER',?,?,?)",uuid(),project,fault.get("relation_id"),fault.get("generation"),actor,state,now());
        return Map.of("replayed",false,"state",state,"pendingCount",pending);
    }
    private static String hash(Object value){
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(io.metersphere.sdk.util.JSON.toJSONString(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static PlanReviewFailure denied(){return new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"当前通知记录不可访问");}
    private static long number(Map<String,Object> row,String key){Object value=row.get(key);return value instanceof Number n?n.longValue():0;}
    private static boolean truth(Object value){return Boolean.TRUE.equals(value)||value instanceof Number n&&n.intValue()!=0;}
    private static long now(){return System.currentTimeMillis();}
    private static String uuid(){return UUID.randomUUID().toString();}
}
