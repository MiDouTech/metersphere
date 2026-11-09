package io.metersphere.functional.handoff;

import io.metersphere.functional.planreview.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static io.metersphere.functional.handoff.HandoffSourceReader.*;
import static io.metersphere.functional.handoff.HandoffStore.*;
import static io.metersphere.functional.handoff.HandoffAuthorization.fail;

@Service
@Transactional(rollbackFor=Exception.class)
public class HandoffService {
    final HandoffStore store;
    final HandoffSourceReader sources;
    final HandoffAuthorization auth;
    final PlanReviewPersistence sourceLocks;
    final HandoffWebhookTarget target;
    final HandoffSecrets secrets;
    final String environment;
    private String sourceBaseUrl="";
    public HandoffService(HandoffStore store,HandoffSourceReader sources,HandoffAuthorization auth,
                          PlanReviewPersistence locks,HandoffWebhookTarget target,HandoffSecrets secrets,
                          @Value("${metersphere.hifox-handoff.source-environment:}") String environment){
        this.store=store;this.sources=sources;this.auth=auth;this.sourceLocks=locks;this.target=target;this.secrets=secrets;this.environment=environment;
    }
    @Value("${metersphere.hifox-handoff.source-base-url:}")
    public void setSourceBaseUrl(String value){
        if(value.isBlank()){sourceBaseUrl="";return;}
        var uri=HandoffHttpTransport.syntax(value);
        if(uri.getRawQuery()!=null||!(uri.getPath().isEmpty()||"/".equals(uri.getPath())))throw new IllegalArgumentException("来源基地址须为HTTPS站点根地址");
        sourceBaseUrl=value.replaceAll("/+$","");
    }
    String key(String project,String plan){
        if(environment.isBlank())throw fail("ENVIRONMENT_NOT_CONFIGURED",503,"尚未配置源环境");
        return HandoffPolicy.businessKey(environment,project,plan);
    }
    static void expected(Map<String,Object> row,long revision){if(number(row,"revision")!=revision)throw stale();}
    static PlanReviewFailure stale(){return fail("PREVIEW_STALE",409,"预览已变化或过期，请重新预览");}
    static void requirePG(Map<String,Object> config){
        if(!truth(config.get("enabled"))||!truth(config.get("external_grant")))throw fail("OUTBOUND_PAUSED",409,"推送未启用或出站授权已暂停");
    }
    void ready(Map<String,Object> config){
        if(config.get("url_secret")==null||sourceBaseUrl.isBlank()||environment.isBlank())throw fail("CONFIG_REQUIRED",409,"请先配置接收地址和MSP来源环境");
    }
    public Map<String,Object> config(String project,String actor){auth.require(actor,project,"CONFIG_MANAGE",false);return safeConfig(store.lock(project));}
    Map<String,Object> safeConfig(Map<String,Object> c){
        return Map.of("revision",number(c,"revision"),"enabled",truth(c.get("enabled")),"external_grant",truth(c.get("external_grant")),
                "configured",c.get("url_secret")!=null,"urlRevision",number(c,"url_revision"),"authMode",text(c,"auth_mode"),
                "tokenConfigured",c.get("token_secret")!=null,"environment",environment,"productVersion","v1.7");
    }
    public Map<String,Object> saveConfig(String project,long revision,HandoffConfiguration input,String actor){
        auth.require(actor,project,"CONFIG_MANAGE",false);var c=store.lock(project);expected(c,revision);
        if(input==null||!Set.of("NONE","BEARER").contains(Objects.toString(input.authMode(),"")))throw fail("INVALID_CONFIG",400,"认证模式无效");
        if(input.url()!=null && (input.url().length()>4096 || input.url().codePoints().anyMatch(Character::isISOControl)))throw fail("INVALID_URL",400,"接收地址格式不安全或超过4096字符");
        String url=input.url()==null?"":input.url().strip();
        String old=c.get("url_secret")==null?null:secrets.open(project,"url",text(c,"url_secret"));
        if(url.isEmpty())url=old;
        if(url==null)throw fail("URL_REQUIRED",400,"请填写接收地址");
        target.validate(url); // DNS/security validation only; never HEAD/GET/POST.
        String token=input.token()==null?"":input.token();
        if(token.length()>8192||token.codePoints().anyMatch(ch->ch<33||ch>126))throw fail("INVALID_TOKEN",400,"Token格式无效");
        if("NONE".equals(input.authMode())){
            if(c.get("token_secret")!=null&&!input.clearToken())throw fail("CLEAR_CONFIRMATION_REQUIRED",400,"请明确确认清除认证");
            token=null;
        }else if(token.isEmpty()){
            token=c.get("token_secret")==null?null:secrets.open(project,"token",text(c,"token_secret"));
            if(token==null)throw fail("TOKEN_REQUIRED",400,"Bearer模式请填写Token");
        }
        boolean changed=!Objects.equals(old,url);long urlRevision=number(c,"url_revision")+(changed?1:0);
        store.jdbc.update("UPDATE hifox_http_config SET url_secret=?,token_secret=?,auth_mode=?,url_revision=?,external_grant=CASE WHEN ? THEN 0 ELSE external_grant END,credential_revision=credential_revision+1,revision=revision+1,update_time=? WHERE project_id=?",
                secrets.seal(project,"url",url),token==null?null:secrets.seal(project,"token",token),input.authMode(),urlRevision,changed,System.currentTimeMillis(),project);
        if(changed)store.jdbc.update("UPDATE hifox_http_delivery SET state=CASE WHEN state IN ('SUCCESS','IN_FLIGHT') THEN state ELSE 'URL_CHANGED' END,reason='URL_CHANGED',revision=revision+1 WHERE project_id=? AND attempts_used>0",project);
        store.audit(project,null,actor,"HTTP_CONFIG_SAVED",revision+1);return safeConfig(store.lock(project));
    }
    public Map<String,Object> grant(String project,long revision,boolean external,String actor){
        auth.require(actor,project,"CONFIG_MANAGE",false);var c=store.lock(project);expected(c,revision);
        store.jdbc.update("UPDATE hifox_http_config SET external_grant=?,revision=revision+1,update_time=? WHERE project_id=?",external,System.currentTimeMillis(),project);
        if(!external)pause(project,"PG_REVOKED",actor);
        store.audit(project,null,actor,external?"PG_GRANTED":"PG_REVOKED",revision+1);return safeConfig(store.lock(project));
    }
    public Map<String,Object> disable(String project,long revision,String actor){
        auth.require(actor,project,"CONFIG_MANAGE",false);var c=store.lock(project);expected(c,revision);
        store.jdbc.update("UPDATE hifox_http_config SET enabled=0,revision=revision+1,update_time=? WHERE project_id=?",System.currentTimeMillis(),project);
        store.jdbc.update("UPDATE hifox_http_interval SET end_json=?,active_project_id=NULL WHERE active_project_id=?",json(snapshotIds(project)),project);
        pause(project,"DISABLED",actor);store.audit(project,null,actor,"DISABLED",revision+1);return safeConfig(store.lock(project));
    }
    private void pause(String project,String reason,String actor){
        var affected=store.jdbc.queryForList("SELECT business_key,revision FROM hifox_http_delivery WHERE project_id=? AND state IN ('READY','RETRY','IN_FLIGHT','PAUSED')",project);
        for(var d:affected)store.audit(project,text(d,"business_key"),actor,"PAUSE_"+reason,number(d,"revision")+1);
        store.jdbc.update("UPDATE hifox_http_delivery SET state=CASE WHEN state='IN_FLIGHT' THEN state ELSE 'PAUSED' END,reason=?,revision=revision+1 WHERE project_id=? AND state IN ('READY','RETRY','IN_FLIGHT','PAUSED')",reason,project);
    }
    /** Source transaction hook. Only real committed completion snapshots provide occurred_at. */
    @Transactional(noRollbackFor=Exception.class)
    public void capture(String project,String plan){capture(project,plan,false);}
    @Transactional(noRollbackFor=Exception.class)
    public void capture(String project,String plan,boolean mutation){
        captureCore(project,plan,mutation);
    }
    @Transactional(noRollbackFor=Exception.class)
    void captureCore(String project,String plan,boolean mutation){
        if(environment.isBlank())return;
        var c=store.lock(project);sourceLocks.lockSource(plan);
        var s=sources.read(project,plan);boolean eligible=HandoffPolicy.evaluate(s.snapshot()).eligible();String key=key(project,plan);
        var rows=store.jdbc.queryForList("SELECT * FROM hifox_http_fact WHERE business_key=? FOR UPDATE",key);var before=rows.isEmpty()?Map.<String,Object>of():rows.getFirst();
        if(!s.snapshot().sourceAuthorized()&&!before.isEmpty()){
            var paused=store.delivery(project,key);
            if(!paused.isEmpty()&&!"SOURCE_ACCESS_UNAVAILABLE".equals(paused.get("reason"))){
                store.jdbc.update("UPDATE hifox_http_delivery SET state=CASE WHEN state IN ('SUCCESS','IN_FLIGHT','SOURCE_CHANGED','URL_CHANGED') THEN state ELSE 'PAUSED' END,reason='SOURCE_ACCESS_UNAVAILABLE',revision=revision+1 WHERE business_key=?",key);
                store.audit(project,key,"SYSTEM","PAUSE_SOURCE_ACCESS_UNAVAILABLE",number(paused,"revision")+1);
            }
            return;
        }
        Long passed=eligible&&s.frozenAt()>0?s.frozenAt():null;
        String evidence=passed==null?null:HandoffPolicy.hash(json(List.of(passed,s.snapshot().generation(),s.snapshot().sourceVersion(),s.snapshot().frozenVersion(),s.snapshot().frozen(),s.snapshot().opinionFingerprint(),s.snapshotId(),s.ruleRevision())));
        boolean[] membership=membership(project,s.snapshotId());boolean known=membership[0];boolean automatic=membership[1];
        if(Objects.equals(before.get("snapshot_hash"),s.fingerprint())&&Objects.equals(before.get("time_revision"),evidence)
                &&truth(before.get("interval_known"))==known&&truth(before.get("automatic"))==automatic&&Objects.equals(before.get("snapshot_json"),json(s)))return;
        long seq=store.next(project);
        long revision=number(before,"revision")+1;
        store.jdbc.update("INSERT INTO hifox_http_fact(business_key,project_id,plan_id,revision,event_sequence,eligible,passed_at,time_revision,interval_known,automatic,snapshot_hash,snapshot_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE revision=VALUES(revision),event_sequence=VALUES(event_sequence),eligible=VALUES(eligible),passed_at=VALUES(passed_at),time_revision=VALUES(time_revision),interval_known=VALUES(interval_known),automatic=VALUES(automatic),snapshot_hash=VALUES(snapshot_hash),snapshot_json=VALUES(snapshot_json)",
                key,project,plan,revision,seq,eligible,passed,evidence,known,automatic,s.fingerprint(),json(s));
        var d=store.delivery(project,key);
        if(!d.isEmpty()){
            if(number(d,"attempts_used")>0&&(!eligible||!Objects.equals(d.get("time_revision"),evidence))){
                store.jdbc.update("UPDATE hifox_http_delivery SET source_changed=1,state=CASE WHEN state IN ('SUCCESS','IN_FLIGHT') THEN state ELSE 'SOURCE_CHANGED' END,reason='SOURCE_CHANGED',revision=revision+1 WHERE business_key=?",key);
            }else if(number(d,"attempts_used")==0){
                store.jdbc.update("UPDATE hifox_http_delivery SET snapshot_json=?,fact_revision=?,time_revision=?,payload_json=NULL,payload_hash=NULL,state=CASE WHEN ? THEN state ELSE 'CANCELLED' END,revision=revision+1 WHERE business_key=?",json(s),revision,Objects.toString(evidence,""),eligible,key);
                if(eligible&&passed!=null&&automatic&&"CANCELLED".equals(d.get("state"))&&!Set.of("DISABLED","PG_REVOKED").contains(text(d,"reason")))store.jdbc.update("UPDATE hifox_http_delivery SET state='READY' WHERE business_key=?",key);
            }
        }else if(eligible&&passed!=null&&automatic&&!store.legacy(project,plan))candidate(project,s,revision,evidence,"AUTOMATIC");
    }
    @Transactional(noRollbackFor=Exception.class)
    public void sourceDeleted(String project,String plan){
        if(environment.isBlank())return;store.lock(project);
        store.jdbc.update("UPDATE hifox_http_fact SET eligible=0,passed_at=NULL,time_revision=NULL,revision=revision+1 WHERE business_key=?",key(project,plan));
        store.jdbc.update("UPDATE hifox_http_delivery SET source_changed=1,state=CASE WHEN state IN ('SUCCESS','IN_FLIGHT') THEN state ELSE 'SOURCE_CHANGED' END,reason='SOURCE_CHANGED',revision=revision+1 WHERE business_key=?",key(project,plan));
    }
    void candidate(String project,HandoffSourceReader.Source s,long revision,String evidence,String kind){
        long now=System.currentTimeMillis();
        store.jdbc.update("INSERT IGNORE INTO hifox_http_delivery(business_key,project_id,plan_id,event_id,state,candidate_kind,snapshot_json,fact_revision,time_revision,create_time,update_time) VALUES(?,?,?,?,'READY',?,?,?,?,?,?)",
                key(project,s.plan()),project,s.plan(),UUID.randomUUID().toString(),kind,json(s),revision,evidence,now,now);
    }
    private List<String> snapshotIds(String project){return store.jdbc.queryForList("SELECT id FROM plan_review_frozen_snapshot WHERE project_id=? AND object_side='review' ORDER BY id",String.class,project);}
    /** Snapshot rows are immutable and written under the same project mutex as enable/disable. */
    private boolean[] membership(String project,String snapshot){
        if(snapshot==null||snapshot.isBlank())return new boolean[]{false,false};
        boolean known=false;
        for(var interval:store.jdbc.queryForList("SELECT * FROM hifox_http_interval WHERE project_id=?",project)){
            var baseline=decode(interval.get("baseline_json"),List.class);
            if(baseline.contains(snapshot)){known=true;continue;}
            if(interval.get("active_project_id")!=null || interval.get("end_json")!=null && decode(interval.get("end_json"),List.class).contains(snapshot))return new boolean[]{true,true};
        }
        return new boolean[]{known,false};
    }
    Map<String,Object> fact(String project,String plan){capture(project,plan);return store.jdbc.queryForMap("SELECT * FROM hifox_http_fact WHERE business_key=?",key(project,plan));}
    void requireTime(Map<String,Object> f){if(f.get("passed_at")==null||text(f,"time_revision").isBlank())throw fail("ORIGINAL_TIME_UNVERIFIED",409,"缺少可核实的原通过时间，核实后请重新预览");}
    /** Full inventory stays server-side; permissions are applied before building any response. */
    List<Map<String,Object>> inventory(String project){
        var result=new ArrayList<Map<String,Object>>();
        for(String plan:store.jdbc.queryForList("SELECT id FROM test_plan WHERE project_id=? AND type='TEST_PLAN' ORDER BY id",String.class,project)){
            capture(project,plan);var f=store.jdbc.queryForMap("SELECT * FROM hifox_http_fact WHERE business_key=?",key(project,plan));
            result.add(f);
        }
        return result;
    }
    String permissionRevision(String project,String actor){
        // This checkout's authority grants source reads at project scope; no guessed role-name shortcuts.
        return HandoffPolicy.hash(json(List.of(
                store.jdbc.queryForList("SELECT r.role_id,r.source_id,role.enabled,p.permission_id FROM user_role_relation r JOIN user_role role ON role.id=r.role_id LEFT JOIN user_role_permission p ON p.role_id=role.id WHERE r.user_id=? AND r.source_id IN (?, 'system') ORDER BY r.role_id,r.source_id,p.permission_id",actor,project),
                store.jdbc.queryForList("SELECT enable,deleted FROM user WHERE id=?",actor),
                store.jdbc.queryForList("SELECT enable,deleted FROM project WHERE id=?",project))));
    }
    boolean readable(String actor,String project){try{auth.read(actor,project);return true;}catch(PlanReviewFailure denied){return false;}}
    String stamp(String project,String plan,String action,String actor){
        Object facts;
        if("ENABLE".equals(action)){try{facts=List.of(inventory(project),snapshotIds(project));}catch(RuntimeException unavailable){throw fail("INVENTORY_UNAVAILABLE",503,"历史盘点暂不可用，请重试");}}
        else{var f=fact(project,plan);var d=store.delivery(project,key(project,plan));facts=List.of(f,d,store.legacyRows(project,plan));}
        var c=store.lock(project);return HandoffPolicy.hash(json(List.of(c,permissionRevision(project,actor),facts)));
    }
    void sendAllowed(String project,String plan,String action,String actor){
        auth.require(actor,project,"PLAN_MANAGE",true);var c=store.lock(project);requirePG(c);ready(c);
        var f=fact(project,plan);requireTime(f);
        if(!HandoffPolicy.evaluate(sources.read(project,plan).snapshot()).eligible())throw fail("SOURCE_NOT_ELIGIBLE",409,"当前来源不合格或待核实");
        if(!truth(f.get("eligible")))throw fail("SOURCE_NOT_ELIGIBLE",409,"当前来源不合格或待核实");
        if(store.legacy(project,plan)&&!("MIGRATE_UNSENT".equals(action)&&store.provenLegacyUnsent(project,plan)))throw fail("LEGACY_READ_ONLY",409,"旧版结果待人工核对，只有可证明从未发送的记录可单条预览迁移");
        if("MIGRATE_UNSENT".equals(action)&&!store.legacy(project,plan))throw fail("NO_LEGACY_RECORD",409,"没有待迁移的旧记录");
        var d=store.delivery(project,key(project,plan));
        if(!d.isEmpty()&&(number(d,"attempts_used")>=4||!Set.of("PAUSED","FAILED","CANCELLED").contains(text(d,"state"))))throw fail("NO_REMAINING_ACTION",409,"当前记录不能补推或恢复");
        if(!d.isEmpty()&&(truth(d.get("source_changed"))||number(d,"attempts_used")>0&&number(d,"url_revision")!=number(c,"url_revision")))throw fail("SOURCE_OR_URL_CHANGED",409,"来源或接收地址已变化，原消息已停止");
        if(!Set.of("PUSH","RESUME","MIGRATE_UNSENT").contains(action))throw fail("INVALID_ACTION",400,"操作无效");
    }
    public Map<String,Object> preview(String project,String plan,String action,boolean newBudget,String actor){
        if(newBudget)throw fail("BUDGET_FIXED",400,"累计最多四次，不支持新增预算");
        if("ENABLE".equals(action)){
            auth.require(actor,project,"CONFIG_MANAGE",false);var c=store.lock(project);ready(c);
            if(truth(c.get("enabled")))throw fail("ALREADY_ENABLED",409,"已启用");
        }else sendAllowed(project,plan,action,actor);
        String hash=stamp(project,plan,action,actor),id=UUID.randomUUID().toString();long expiry=System.currentTimeMillis()+600_000;
        var view=new LinkedHashMap<String,Object>();view.put("scope","仅功能测试，排除API/场景");view.put("configuration",safeConfig(store.lock(project)));
        if("ENABLE".equals(action)){
            if(readable(actor,project)){
                var visible=inventory(project).stream().filter(f->truth(f.get("eligible"))).map(f->{var s=decode(f.get("snapshot_json"),HandoffSourceReader.Source.class);return Map.of("planId",s.plan(),"planName",s.planName(),"reviewId",s.review(),"functionalCount",s.snapshot().source().size());}).toList();
                view.put("visible",visible);view.put("visibleCount",visible.size());
            }
            view.put("visibilityMessage","历史明细按来源读取权限展示；当前无可展示明细不表示历史记录为空。可见数量非项目历史总量。");
        }else{
            var f=fact(project,plan);var d=store.delivery(project,key(project,plan));
            var source=decode(f.get("snapshot_json"),HandoffSourceReader.Source.class);
            view.put("inspection",inspection(source,text(f,"time_revision"),d,store.lock(project)));
            view.put("occurredAt",Instant.ofEpochMilli(number(f,"passed_at")).toString());view.put("timeRevision",f.get("time_revision"));view.put("interval",interval(f));view.put("attemptsUsed",number(d,"attempts_used"));view.put("remaining",4-number(d,"attempts_used"));
        }
        store.jdbc.update("INSERT INTO hifox_handoff_preview(id,project_id,business_key,actor,action,revision_hash,payload_json,expires_at) VALUES(?,?,?,?,?,?,?,?)",id,project,"ENABLE".equals(action)?null:key(project,plan),actor,action,hash,json(Map.of("plan",plan)),expiry);
        return Map.of("previewId",id,"action",action,"expiresAt",expiry,"message",risk(),"payload",view);
    }
    static String risk(){return "HTTP 2xx不代表建单或通知成功；超时等最多重试3次，接收平台可能重复处理。不新增总预算。";}
    static String interval(Map<String,Object> f){return !truth(f.get("interval_known"))?"通过时间已核实，启用区间归属待确认":truth(f.get("automatic"))?"启用区间内":"历史待补推";}
    public Map<String,Object> confirm(String project,String id,boolean acknowledged,String actor){
        var previews=store.jdbc.queryForList("SELECT * FROM hifox_handoff_preview WHERE id=? AND project_id=? AND actor=? FOR UPDATE",id,project,actor);
        if(previews.size()!=1||!acknowledged)throw stale();var p=previews.getFirst();String action=text(p,"action");
        String plan=text(decode(p.get("payload_json"),Map.class),"plan");
        if("ENABLE".equals(action))auth.require(actor,project,"CONFIG_MANAGE",false);else auth.require(actor,project,"PLAN_MANAGE",true);
        if(p.get("consumed_at")!=null)return decode(p.get("result_json"),Map.class);
        if(number(p,"expires_at")<System.currentTimeMillis()||!Objects.equals(p.get("revision_hash"),stamp(project,plan,action,actor)))throw stale();
        var c=store.lock(project);String operation=UUID.randomUUID().toString();
        if("ENABLE".equals(action)){
            ready(c);if(truth(c.get("enabled")))throw stale();
            store.jdbc.update("INSERT INTO hifox_http_interval(id,project_id,active_project_id,start_sequence,baseline_json) VALUES(?,?,?,?,?)",UUID.randomUUID().toString(),project,project,number(c,"event_sequence"),json(snapshotIds(project)));
            store.jdbc.update("UPDATE hifox_http_config SET enabled=1,cutover=event_sequence,revision=revision+1,update_time=? WHERE project_id=?",System.currentTimeMillis(),project);
        }else{
            sendAllowed(project,plan,action,actor);var f=fact(project,plan);var s=decode(f.get("snapshot_json"),HandoffSourceReader.Source.class);
            if("MIGRATE_UNSENT".equals(action)){
                if(!store.provenLegacyUnsent(project,plan))throw fail("LEGACY_READ_ONLY",409,"无法证明旧记录从未发送，禁止迁移发送");
                for(var legacy:store.legacyRows(project,plan)){
                    store.jdbc.update("INSERT INTO hifox_http_legacy_migration(legacy_key,project_id,business_key,original_state,actor,confirmation_id,create_time) VALUES(?,?,?,?,?,?,?)",legacy.get("business_key"),project,key(project,plan),legacy.get("state"),actor,id,System.currentTimeMillis());
                    store.jdbc.update("UPDATE hifox_handoff_delivery SET protocol_version=17,state='MIGRATED_HTTP17',revision=revision+1 WHERE business_key=?",legacy.get("business_key"));
                }
            }
            candidate(project,s,number(f,"revision"),text(f,"time_revision"),"MANUAL");
            store.jdbc.update("UPDATE hifox_http_delivery SET state='READY',reason=NULL,next_at=0,operation_id=?,revision=revision+1 WHERE business_key=?",operation,key(project,plan));
        }
        var result=Map.<String,Object>of("operationId",operation,"message","ENABLE".equals(action)?"已启用；仅自动处理启用后新通过计划，历史计划不自动补发":"已加入异步投递队列");
        store.jdbc.update("UPDATE hifox_handoff_preview SET consumed_at=?,result_json=? WHERE id=?",System.currentTimeMillis(),json(result),id);
        store.audit(project,"ENABLE".equals(action)?null:text(p,"business_key"),actor,action,"ENABLE".equals(action)?number(c,"revision"):number(store.delivery(project,key(project,plan)),"revision"));return result;
    }
    byte[] payload(String project,Map<String,Object> d,Map<String,Object> f){
        requireTime(f);var s=decode(d.get("snapshot_json"),HandoffSourceReader.Source.class);var snap=s.snapshot();
        var relation=store.jdbc.queryForMap("SELECT id,generation FROM plan_review_relation WHERE project_id=? AND active_plan_id=? AND state='ACTIVE'",project,s.plan());
        String projectName=store.jdbc.queryForObject("SELECT name FROM project WHERE id=?",String.class,project);
        if(sourceBaseUrl.isBlank()||projectName==null||snap.opinionFingerprint().isBlank()||snap.generation()==null)throw fail("SOURCE_FIELDS_MISSING",409,"权威来源字段缺失");
        var plan=new LinkedHashMap<String,Object>();plan.put("id",s.plan());plan.put("name",s.planName());plan.put("identifier",s.planNumber().isBlank()?null:s.planNumber());plan.put("url",sourceBaseUrl+"/#/test-plan/testPlanIndexDetail?id="+encode(s.plan()));
        var review=new LinkedHashMap<String,Object>();review.put("id",s.review());review.put("name",s.reviewName());review.put("identifier",s.reviewNumber().isBlank()?null:s.reviewNumber());review.put("url",sourceBaseUrl+"/#/test-plan/caseReviewDetail?id="+encode(s.review()));
        var data=new LinkedHashMap<String,Object>();data.put("schema_version","1");data.put("event_type","functional_test_plan.review_passed");data.put("event_id",d.get("event_id"));data.put("business_key",d.get("business_key"));
        data.put("occurred_at",Instant.ofEpochMilli(number(f,"passed_at")).toString());data.put("snapshot_at",Instant.now().toString());
        data.put("source",Map.of("environment",environment,"project_id",project,"project_name",projectName));data.put("plan",plan);data.put("review",review);data.put("relation",relation);
        data.put("qualification",Map.of("scope","FUNCTIONAL","excluded_scopes",List.of("API","SCENARIO"),"functional_case_count",snap.source().size(),"source_revision",s.fingerprint(),"member_version",Long.toString(snap.sourceVersion()),"rule_revision",s.ruleRevision(),"snapshot_hash",s.snapshotHash()));
        data.put("title","[MSP功能测试] "+(s.planNumber().isBlank()?s.plan():s.planNumber())+" "+s.planName());
        data.put("summary","功能用例"+snap.source().size()+"条评审通过；接口/场景未纳入本次评审。评审通过表示范围交接，不代表测试执行完成。执行前请回MSP核对当前范围。");
        byte[] bytes=json(data).getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>65536)throw fail("PAYLOAD_TOO_LARGE",409,"消息超过64 KiB，请检查来源名称");return bytes;
    }
    private static String encode(String value){return java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8);}
    private HandoffInspection inspection(HandoffSourceReader.Source source,String evidenceRevision,Map<String,Object> delivery,Map<String,Object> config){
        var reasons=new TreeSet<String>();String reason=text(delivery,"reason");
        if(!reason.isBlank())reasons.add(reason);
        if(!delivery.isEmpty()&&!"SUCCESS".equals(delivery.get("state"))&&!reason.isBlank()){
            var actions=store.jdbc.queryForList("SELECT action FROM hifox_handoff_audit WHERE project_id=? AND business_key=? AND action LIKE 'PAUSE!_%' ESCAPE '!' AND revision>COALESCE((SELECT MAX(revision) FROM hifox_handoff_audit WHERE project_id=? AND business_key=? AND action IN ('RESUME','PUSH','MIGRATE_UNSENT')),0)",String.class,source.project(),delivery.get("business_key"),source.project(),delivery.get("business_key"));
            for(String action:actions)if(action.startsWith("PAUSE_"))reasons.add(action.substring(6));
        }
        if(!truth(config.get("enabled")))reasons.add("DISABLED");
        if(!truth(config.get("external_grant")))reasons.add("PG_REVOKED");
        return new HandoffInspection(new HandoffInspection.TimeEvidence(source.snapshotId(),evidenceRevision,source.frozenAt()>0?Instant.ofEpochMilli(source.frozenAt()).toString():null),
                new HandoffInspection.SourceVersion(source.fingerprint(),Long.toString(source.snapshot().sourceVersion()),source.ruleRevision(),source.snapshotHash()),
                source.snapshot().source().size(),delivery.get("payload_json")==null?null:text(delivery,"event_id"),List.copyOf(reasons));
    }
    Map<String,Object> safeDelivery(Map<String,Object> d){
        var result=new LinkedHashMap<String,Object>();
        for(String field:List.of("business_key","event_id","operation_id","state","reason","attempts_used","http_status","next_at","had_unknown","source_changed","payload_hash","url_revision","revision","create_time","update_time"))result.put(field,d.get(field));
        var s=decode(d.get("snapshot_json"),HandoffSourceReader.Source.class);result.put("source",Map.of("planId",s.plan(),"planName",s.planName(),"reviewId",s.review(),"reviewName",s.reviewName()));
        result.put("inspection",inspection(s,text(d,"time_revision"),d,store.lock(text(d,"project_id"))));
        result.put("audits",store.jdbc.queryForList("SELECT id,actor,action,revision,create_time,business_key FROM hifox_handoff_audit WHERE project_id=? AND (business_key=? OR business_key IS NULL OR business_key='') ORDER BY create_time,id",d.get("project_id"),d.get("business_key")).stream()
                .map(a->new HandoffInspection.Audit(text(a,"id"),text(a,"actor"),text(a,"action"),number(a,"revision"),number(a,"create_time"),text(a,"business_key").isBlank()?"PROJECT":"DELIVERY")).toList());
        result.put("attempts",store.jdbc.queryForList("SELECT sequence_no,started_at,ended_at,http_status,error_code,result_unknown,credential_revision FROM hifox_http_attempt WHERE business_key=? ORDER BY sequence_no",d.get("business_key")));
        if(d.get("payload_json")!=null)result.put("message",decode(d.get("payload_json"),Map.class));
        result.put("risk",risk());return result;
    }
    public Map<String,Object> summary(String project,String plan,String actor){
        auth.read(actor,project);var c=store.lock(project);var f=fact(project,plan);var s=sources.read(project,plan);var d=store.delivery(project,key(project,plan));
        var result=new LinkedHashMap<String,Object>();result.put("eligibility",HandoffPolicy.evaluate(s.snapshot()));result.put("enabled",truth(c.get("enabled")));result.put("canManage",auth.can(actor,project,"PLAN_MANAGE"));
        result.put("timeState",f.get("passed_at")==null?"原通过时间待核实":interval(f));result.put("delivery",d.isEmpty()?Map.of():safeDelivery(d));
        result.put("legacy",store.legacy(project,plan));result.put("legacyCanMigrate",store.legacy(project,plan)&&store.provenLegacyUnsent(project,plan));result.put("risk",risk());return result;
    }
    public Map<String,Object> objectSummary(String project,String side,String id,String actor){
        auth.read(actor,project);if("plan".equals(side))return summary(project,id,actor);
        if(!"review".equals(side)||store.jdbc.queryForObject("SELECT COUNT(*) FROM case_review WHERE project_id=? AND id=?",Long.class,project,id)!=1)throw fail("RESOURCE_NOT_ACCESSIBLE",404,"对象不可访问");
        var plans=store.jdbc.queryForList("SELECT plan_id FROM plan_review_relation WHERE project_id=? AND active_review_id=? AND state='ACTIVE'",String.class,project,id);
        return plans.size()==1?summary(project,plans.getFirst(),actor):Map.of("linked",false);
    }
    public Map<String,Object> list(String project,int page,int size,String keyword,String state,String actor){return list(project,page,size,keyword,state,null,null,null,actor);}
    /** Time filter is inclusive delivery create_time (epoch milliseconds), independent of source_changed. */
    public Map<String,Object> list(String project,int page,int size,String keyword,String state,Boolean sourceChanged,Long from,Long to,String actor){
        auth.require(actor,project,"CONFIG_MANAGE",true);
        if(page<1||size<1||size>100||keyword==null||keyword.length()>255)throw fail("INVALID_PAGE",400,"分页筛选无效");
        if(state!=null&&!Set.of("READY","RETRY","SUCCESS","IN_FLIGHT","PAUSED","FAILED","UNKNOWN_STOPPED","SOURCE_CHANGED","URL_CHANGED","CANCELLED").contains(state))throw fail("INVALID_STATE",400,"状态筛选无效");
        if(from!=null&&from<0||to!=null&&to<0||from!=null&&to!=null&&from>to)throw fail("INVALID_TIME_RANGE",400,"记录创建时间范围无效");
        String where="project_id=?";var args=new ArrayList<Object>();args.add(project);
        if(!keyword.isBlank()){where+=" AND (plan_id LIKE ? OR snapshot_json LIKE ?)";String match="%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%";where=where.replace("LIKE ?","LIKE ? ESCAPE '!'");args.add(match);args.add(match);}
        if(state!=null){where+=" AND state=?";args.add(state);}
        if(sourceChanged!=null){where+=" AND source_changed=?";args.add(sourceChanged);}
        if(from!=null){where+=" AND create_time>=?";args.add(from);}
        if(to!=null){where+=" AND create_time<=?";args.add(to);}
        Long total=store.jdbc.queryForObject("SELECT COUNT(*) FROM hifox_http_delivery WHERE "+where,Long.class,args.toArray());args.add(size);args.add((page-1L)*size);
        var rows=store.jdbc.queryForList("SELECT * FROM hifox_http_delivery WHERE "+where+" ORDER BY create_time DESC,business_key LIMIT ? OFFSET ?",args.toArray());
        return Map.of("total",total,"items",rows.stream().map(this::safeDelivery).toList());
    }
}
