package io.metersphere.functional.planreview;

import com.fasterxml.jackson.core.type.TypeReference;
import io.metersphere.sdk.util.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit, bounded project migration. Flyway never adopts business objects or restarts old jobs. */
@Service
@Transactional(rollbackFor=Exception.class)
public class PlanReviewLegacyMigrationService {
    private final JdbcTemplate jdbc;
    private final PlanReviewAuthorization auth;
    private final PlanReviewPersistence store;
    private final PlanReviewLegacyInventory inventory;
    private final PlanReviewLegacySnapshot snapshots;
    private final PlanReviewApplicationService application;
    private List<PlanReviewLegacyJobAdopter> adopters=List.of();
    private List<PlanReviewLegacySandbox> sandboxes=List.of();
    public PlanReviewLegacyMigrationService(JdbcTemplate jdbc,PlanReviewAuthorization auth,PlanReviewPersistence store,
            PlanReviewLegacyInventory inventory,PlanReviewLegacySnapshot snapshots,PlanReviewApplicationService application){
        this.jdbc=jdbc;this.auth=auth;this.store=store;this.inventory=inventory;this.snapshots=snapshots;this.application=application;
    }
    @Autowired(required=false) public void setAdopters(List<PlanReviewLegacyJobAdopter> values){adopters=List.copyOf(values);}
    @Autowired(required=false) public void setSandboxes(List<PlanReviewLegacySandbox> values){sandboxes=List.copyOf(values);}

    public Map<String,Object> recent(String project,String actor){
        auth.requireRead(actor,project,true);auth.requireRead(actor,project,false);var access=auth.current(actor,project,false);
        if(!access.permissions().contains(PlanReviewPolicy.Permission.LEGACY_MIGRATE)&&!access.permissions().contains(PlanReviewPolicy.Permission.TAKEOVER_APPROVE))throw new PlanReviewFailure("PERMISSION_REQUIRED",403,"需要当前项目迁移或负责人审批权限");
        return Map.of("projectId",project,"items",jdbc.queryForList("SELECT id,project_id,state,snapshot_hash,revision,expires_at,backup_at,rehearsal_id,rollback_mode FROM plan_review_legacy_change WHERE project_id=? ORDER BY create_time DESC,id DESC LIMIT 50",project).stream().map(PlanReviewLegacyMigrationService::safe).toList());
    }

    public Map<String,Object> details(String project,String change,String actor){
        auth.requireRead(actor,project,true);auth.requireRead(actor,project,false);var access=auth.current(actor,project,false);
        if(!access.permissions().contains(PlanReviewPolicy.Permission.LEGACY_MIGRATE)&&!access.permissions().contains(PlanReviewPolicy.Permission.TAKEOVER_APPROVE))throw new PlanReviewFailure("PERMISSION_REQUIRED",403,"需要当前项目迁移或负责人审批权限");
        var row=lock(project,change);var result=new TreeMap<String,Object>(safe(row));var choice=selection(row);
        result.put("selection",choice);result.put("approvedBy",Objects.toString(row.get("approved_by"),""));
        result.put("canExecute",access.permissions().contains(PlanReviewPolicy.Permission.LEGACY_MIGRATE)&&PlanReviewPolicy.allowed(PlanReviewPolicy.Action.PAIR,access));
        result.put("canApprove",access.permissions().contains(PlanReviewPolicy.Permission.TAKEOVER_APPROVE));
        result.put("effects",List.of("保留原计划、评审、成员、意见与已成功进度","停止选定旧任务，仅移交明确选定的原任务","保留关系使用原两对象，成员差异须新M08确认","回退不复活旧任务，不覆盖后续业务写入"));
        result.put("events",jdbc.queryForList("SELECT action,actor,result,safe_reason,create_time FROM plan_review_audit WHERE project_id=? AND operation_id=? ORDER BY create_time,id",project,change));
        return result;
    }

    public Map<String,Object> preview(PlanReviewLegacyDecision choice,String actor){
        executor(choice.projectId(),actor);
        var old=jdbc.queryForList("SELECT * FROM plan_review_legacy_change WHERE project_id=? AND request_key=? FOR UPDATE",choice.projectId(),choice.requestKey());
        if(!old.isEmpty()){
            var row=old.getFirst();
            if(!actor.equals(row.get("requested_by"))||!json(choice).equals(row.get("selection_json")))throw fail("REQUEST_CONFLICT","原请求的执行人或明确选择已变化，请创建新预览");
            return safe(row);
        }
        if(!choice.manifestHash().equals(inventory.scan(choice.projectId(),actor).get("manifestHash")))throw fail("MANIFEST_STALE","历史清单已变化，请重新盘点");
        var data=snapshots.read(choice,true);validatePairs(choice,data);
        var prepared=new TreeMap<String,Object>();
        for(var job:rows(data,"jobs"))if("ADOPT".equals(choice.jobs().get(job.get("job_id")).action()))prepared.put((String)job.get("job_id"),adopter().preview(choice.projectId(),job,choice.jobs().get(job.get("job_id"))));
        var backup=new TreeMap<String,Object>();backup.put("data",data);backup.put("preparedJobs",prepared);
        long now=System.currentTimeMillis();Long last=jdbc.queryForObject("SELECT MAX(create_time) FROM plan_review_legacy_change WHERE project_id=?",Long.class,choice.projectId());
        long order=last==null?now:Math.max(now,last+1);String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO plan_review_legacy_change(id,project_id,request_key,requested_by,manifest_hash,selection_json,snapshot_json,snapshot_hash,state,expires_at,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,'PREVIEW',?,?,?)",
                id,choice.projectId(),choice.requestKey(),actor,choice.manifestHash(),json(choice),json(backup).getBytes(StandardCharsets.UTF_8),PlanReviewLegacySnapshot.hash(data),now+600_000,order,now);
        event(choice.projectId(),id,"LEGACY_PREVIEW",actor,"PREVIEW",PlanReviewLegacySnapshot.hash(data));
        return safe(lock(choice.projectId(),id));
    }
    public Map<String,Object> backup(String project,String change,String actor){
        executor(project,actor);var row=lock(project,change);requireState(row,"PREVIEW","BACKED_UP");fresh(row,false);
        if(row.get("backup_at")==null){jdbc.update("UPDATE plan_review_legacy_change SET backup_at=?,state='BACKED_UP',revision=revision+1,update_time=? WHERE id=?",now(),now(),change);event(project,change,"LEGACY_BACKUP",actor,"BACKED_UP",(String)row.get("snapshot_hash"));}
        return safe(lock(project,change));
    }
    /** The provider must execute against a separately identified database; a client cannot submit a passed receipt. */
    public Map<String,Object> rehearse(String project,String change,String actor){
        executor(project,actor);var row=lock(project,change);requireState(row,"BACKED_UP","REHEARSED");fresh(row,false);
        if("REHEARSED".equals(row.get("state"))){rehearsalProof(row);return safe(row);}
        if(sandboxes.size()!=1)throw fail("ISOLATED_REHEARSAL_UNAVAILABLE","未配置唯一的隔离演练数据库，迁移执行保持关闭");
        var evidence=sandboxes.getFirst().rehearse(project,change,backupData(row),selection(row),(isolated,snapshot,choice)->rehearseIsolated(isolated,snapshot,choice,row,actor));
        if(!Boolean.TRUE.equals(evidence.get("passed"))||!row.get("snapshot_hash").equals(evidence.get("snapshotHash")))throw fail("REHEARSAL_FAILED","隔离演练未通过或备份版本不匹配");
        fresh(row,false);String receipt=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO plan_review_legacy_rehearsal(id,project_id,change_id,snapshot_hash,runner,state,evidence_json,create_time) VALUES(?,?,?,?,?,'PASSED',?,?)",receipt,project,change,row.get("snapshot_hash"),actor,json(evidence),now());
        jdbc.update("UPDATE plan_review_legacy_change SET state='REHEARSED',rehearsal_id=?,revision=revision+1,update_time=? WHERE id=?",receipt,now(),change);
        event(project,change,"LEGACY_REHEARSE",actor,"REHEARSED",(String)row.get("snapshot_hash"));
        return safe(lock(project,change));
    }
    public Map<String,Object> approve(String project,String change,String snapshotHash,String stoppedEvidenceHash,String releaseSha,boolean acknowledged,String actor){
        owner(project,actor);var row=lock(project,change);requireState(row,"REHEARSED");
        if(!acknowledged||!Objects.equals(snapshotHash,row.get("snapshot_hash"))||stoppedEvidenceHash==null||!stoppedEvidenceHash.matches("[a-fA-F0-9]{64}")||releaseSha==null||!releaseSha.matches("[a-fA-F0-9]{40}"))throw fail("EXPLICIT_APPROVAL_REQUIRED","须明确批准原清单，并登记停入口、停旧消费者和兼容版本的证据");
        fresh(row,false);rehearsalProof(row);
        jdbc.update("UPDATE plan_review_legacy_change SET state='APPROVED',approved_by=?,approved_at=?,stop_evidence_hash=?,compatible_release_sha=?,revision=revision+1,update_time=? WHERE id=?",actor,now(),stoppedEvidenceHash,releaseSha,now(),change);
        event(project,change,"LEGACY_APPROVE",actor,"APPROVED",(String)row.get("snapshot_hash"));
        return safe(lock(project,change));
    }
    public Map<String,Object> execute(String project,String change,String snapshotHash,boolean acknowledged,String actor){
        executor(project,actor);var row=lock(project,change);
        if(!acknowledged||!Objects.equals(snapshotHash,row.get("snapshot_hash")))throw fail("EXPLICIT_APPROVAL_REQUIRED","请确认原备份清单后执行");
        if("APPLIED".equals(row.get("state")))return result(row);
        requireState(row,"APPROVED");owner(project,(String)row.get("approved_by"));rehearsalProof(row);fresh(row,false);
        return apply(row,actor);
    }
    private Map<String,Object> apply(Map<String,Object> row,String actor){
        var choice=selection(row);String project=choice.projectId(),change=(String)row.get("id");var backup=backupData(row);var data=map(backup.get("data"));
        validatePairs(choice,data);
        // Acquire every old worker row before retiring any edge. Old v1 workers recheck PENDING under this lock.
        for(var job:rows(data,"jobs")){
            jdbc.queryForList("SELECT job_id FROM functional_import_followup WHERE job_id=? AND project_id=? FOR UPDATE",job.get("job_id"),project);
            jdbc.update("UPDATE functional_import_followup SET state='MIGRATED_V2',update_time=? WHERE job_id=? AND project_id=?",now(),job.get("job_id"),project);
            var marks=rows(data,"watermarks").stream().filter(mark->Objects.equals(job.get("job_id"),mark.get("job_id"))).toList();
            jdbc.update("INSERT INTO plan_review_legacy_job_map(project_id,job_id,change_id,action,original_state,original_watermark_json,state) VALUES(?,?,?,?,?,?,'STOPPED')",project,job.get("job_id"),change,choice.jobs().get(job.get("job_id")).action(),job.get("state"),json(marks));
        }
        for(var group:rows(data,"groups")){
            String action=choice.groups().get(group.get("id"));
            jdbc.update("INSERT INTO plan_review_legacy_group_map(project_id,legacy_group_id,change_id,action,original_json,plan_id,review_id,state) VALUES(?,?,?,?,?,?,?,?)",project,group.get("id"),change,action,json(group),group.get("plan_id"),group.get("review_id"),"KEEP".equals(action)?"PENDING":"ISOLATE".equals(action)?"ISOLATED":"RELEASED");
            jdbc.update("DELETE FROM functional_import_followup_group WHERE id=? AND project_id=?",group.get("id"),project);
        }
        var relations=new TreeMap<String,Map<String,Object>>();
        for(var group:rows(data,"groups"))if("KEEP".equals(choice.groups().get(group.get("id")))){
            String pair=group.get("plan_id")+":"+group.get("review_id");
            var relation=relations.computeIfAbsent(pair,key->application.adoptLegacyPair(project,(String)group.get("plan_id"),(String)group.get("review_id"),change,actor));
            jdbc.update("UPDATE plan_review_legacy_group_map SET state='APPLIED',relation_id=?,generation=?,created_relation=? WHERE project_id=? AND legacy_group_id=? AND change_id=?",relation.get("relationId"),relation.get("generation"),relation.get("created"),project,group.get("id"),change);
        }
        var prepared=map(backup.get("preparedJobs"));var jobResults=new ArrayList<Map<String,Object>>();
        for(var job:rows(data,"jobs"))if("ADOPT".equals(choice.jobs().get(job.get("job_id")).action())){
            String pair=job.get("plan_id")+":"+job.get("review_id");
            if(job.get("plan_id")!=null&&job.get("review_id")!=null)relations.computeIfAbsent(pair,key->application.adoptLegacyPair(project,(String)job.get("plan_id"),(String)job.get("review_id"),change,actor));
            var relation=relations.getOrDefault(pair,Map.of());
            var outcome=adopter().adopt(project,change,job,map(prepared.get(job.get("job_id"))),relation,(String)row.get("approved_by"));jobResults.add(outcome);
            jdbc.update("UPDATE plan_review_legacy_job_map SET state='ADOPTED',operation_id=?,reserved_plan_id=?,reserved_review_id=? WHERE project_id=? AND job_id=? AND change_id=?",outcome.get("operationId"),outcome.get("planId"),outcome.get("reviewId"),project,job.get("job_id"),change);
            if(!relation.isEmpty())jdbc.update("UPDATE plan_review_legacy_job_map SET relation_id=?,generation=?,created_relation=? WHERE project_id=? AND job_id=? AND change_id=?",relation.get("relationId"),relation.get("generation"),relation.get("created"),project,job.get("job_id"),change);
        }
        var outcome=new TreeMap<String,Object>();outcome.put("projectId",project);outcome.put("changeId",change);outcome.put("relations",List.copyOf(relations.values()));outcome.put("jobs",jobResults);outcome.put("state","APPLIED");outcome.put("memberCatchup","FRESH_M08_REQUIRED");
        String after=afterHash(choice,change);
        jdbc.update("UPDATE plan_review_legacy_change SET state='APPLIED',result_json=?,after_hash=?,revision=revision+1,update_time=? WHERE id=?",json(outcome),after,now(),change);
        event(project,change,"LEGACY_EXECUTE",actor,"APPLIED",(String)row.get("snapshot_hash"));
        return outcome;
    }
    private Map<String,Object> rehearseIsolated(JdbcTemplate isolated,Map<String,Object> backup,PlanReviewLegacyDecision choice,Map<String,Object> original,String actor){
        if(databaseIdentity(jdbc).equals(databaseIdentity(isolated)))throw fail("REHEARSAL_NOT_ISOLATED","演练数据库与当前项目数据库相同，已拒绝");
        var manager=new org.springframework.jdbc.datasource.DataSourceTransactionManager(Objects.requireNonNull(isolated.getDataSource()));
        var transaction=new org.springframework.transaction.support.TransactionTemplate(manager);
        return transaction.execute(status->{
            var cloneAuth=new PlanReviewAuthorization(isolated);var cloneStore=new PlanReviewPersistence(isolated);
            var resolver=new PlanReviewPlanStateResolver(List.of());var cloneSnapshots=new PlanReviewLegacySnapshot(isolated,resolver);
            var cloneApp=new PlanReviewApplicationService(isolated,cloneStore,cloneAuth,manager,resolver);
            var clone=new PlanReviewLegacyMigrationService(isolated,cloneAuth,cloneStore,new PlanReviewLegacyInventory(isolated,cloneAuth,resolver),cloneSnapshots,cloneApp);
            if(hasAdoption(choice)){var cloneAdopter=adopter().forDatabase(isolated);cloneAdopter.prepareSandbox(backup);clone.setAdopters(List.of(cloneAdopter));}
            // Permissions are the current real executor's metadata, copied into the isolated fixture only.
            seedPrincipal(isolated,actor,choice.projectId(),PlanReviewAuthorization.resourceIds(auth.current(actor,choice.projectId(),true)));
            String owner="msp12-isolated-owner";
            seedPrincipal(isolated,owner,choice.projectId(),List.of("PLAN_REVIEW:TAKEOVER_APPROVE","PROJECT_TEST_PLAN:READ","CASE_REVIEW:READ"));
            String hash=PlanReviewLegacySnapshot.hash(cloneSnapshots.read(choice,true));String change=(String)original.get("id");
            isolated.update("INSERT INTO plan_review_legacy_change(id,project_id,request_key,requested_by,manifest_hash,selection_json,snapshot_json,snapshot_hash,state,expires_at,backup_at,rehearsal_id,approved_by,approved_at,stop_evidence_hash,compatible_release_sha,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,'APPROVED',?,?,?,?,?,?,?,?,?)",change,choice.projectId(),choice.requestKey(),actor,choice.manifestHash(),json(choice),json(backup).getBytes(StandardCharsets.UTF_8),hash,now()+600_000,now(),"isolated-fixture",owner,now(),"0".repeat(64),"0".repeat(40),now(),now());
            isolated.update("INSERT INTO plan_review_legacy_rehearsal(id,project_id,change_id,snapshot_hash,runner,state,evidence_json,create_time) VALUES('isolated-fixture',?,?,?,?,'PASSED','{}',?)",choice.projectId(),change,hash,owner,now());
            var beforeFacts=businessFacts(cloneSnapshots.read(choice,true));Object savepoint=status.createSavepoint();
            var first=clone.execute(choice.projectId(),change,hash,true,actor);
            if(!json(first).equals(json(clone.execute(choice.projectId(),change,hash,true,actor))))throw fail("REHEARSAL_REPLAY_FAILED","迁移重跑改变了原结果");
            if(!beforeFacts.equals(businessFacts(cloneSnapshots.read(choice,true,true))))throw fail("REHEARSAL_FACTS_CHANGED","迁移改变了原成员、意见或成功水位");
            var rollback=clone.rollbackPreview(choice.projectId(),change,actor);String previewHash=(String)rollback.get("previewHash");
            if(!"RESTORE_METADATA".equals(rollback.get("mode")))throw fail("REHEARSAL_ROLLBACK_FAILED","无新写入时未能生成元数据回退方案");
            clone.approveRollback(choice.projectId(),change,previewHash,true,owner);clone.rollback(choice.projectId(),change,previewHash,true,actor);
            if(!beforeFacts.equals(businessFacts(cloneSnapshots.read(choice,true,true))))throw fail("REHEARSAL_ROLLBACK_FAILED","回退改变了业务事实");
            status.rollbackToSavepoint(savepoint);status.releaseSavepoint(savepoint);
            clone.execute(choice.projectId(),change,hash,true,actor);
            // A later opinion is a committed business fact, never a reason to restore stale content.
            var reviews=rows(map(backup.get("data")),"reviews");boolean compensationTested=!reviews.isEmpty();String factId=UUID.randomUUID().toString();
            if(compensationTested){
                isolated.update("INSERT INTO case_review_history(id,review_id,case_id,status,content,deleted,abandoned) VALUES(?,?,?,'PASS',?,0,0)",factId,reviews.getFirst().get("id"),"isolated-only-case","isolated-only-opinion".getBytes(StandardCharsets.UTF_8));
                var compensation=clone.rollbackPreview(choice.projectId(),change,actor);
                if(!"COMPENSATE_KEEP_FACTS".equals(compensation.get("mode")))throw fail("REHEARSAL_COMPENSATION_FAILED","后续意见写入未触发保留事实的补偿方案");
                String confirmation=(String)compensation.get("previewHash");clone.approveRollback(choice.projectId(),change,confirmation,true,owner);clone.rollback(choice.projectId(),change,confirmation,true,actor);
                if(isolated.queryForObject("SELECT COUNT(*) FROM case_review_history WHERE id=?",Integer.class,factId)!=1)throw fail("REHEARSAL_COMPENSATION_FAILED","补偿覆盖了后续意见");
            }
            return Map.<String,Object>of("passed",true,"snapshotHash",original.get("snapshot_hash"),"databaseIdentityHash",PlanReviewLegacyInventory.hash(databaseIdentity(isolated)),"sameExecutor",true,"idempotentReplay",true,"metadataRollback",true,"compensationTested",compensationTested,"sourceFactsUnchanged",true);
        });
    }
    private static void seedPrincipal(JdbcTemplate jdbc,String actor,String project,List<String> resources){
        jdbc.update("INSERT IGNORE INTO user(id,enable,deleted) VALUES(?,1,0)",actor);String role="rehearsal-"+Integer.toUnsignedString(actor.hashCode());
        jdbc.update("INSERT IGNORE INTO user_role(id,type,enabled) VALUES(?,'PROJECT',1)",role);
        jdbc.update("INSERT INTO user_role_relation(user_id,role_id,source_id) VALUES(?,?,?)",actor,role,project);
        for(String permission:resources)jdbc.update("INSERT INTO user_role_permission(role_id,permission_id) VALUES(?,?)",role,permission);
    }
    private static String databaseIdentity(JdbcTemplate jdbc){return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)connection->{
        if(!"MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()))return connection.getMetaData().getURL();
        try(var statement=connection.createStatement();var result=statement.executeQuery("SELECT CONCAT(@@server_uuid,':',DATABASE())")){result.next();return result.getString(1);}
    });}
    private static String businessFacts(Map<String,Object> data){var facts=new TreeMap<String,Object>();for(String key:List.of("plans","reviews","sourceMembers","reviewMembers","opinionReferences","watermarks"))facts.put(key,data.get(key));return PlanReviewLegacyInventory.hash(facts);}
    public Map<String,Object> rollbackPreview(String project,String change,String actor){
        executor(project,actor);var row=lock(project,change);requireState(row,"APPLIED");
        String current=afterHash(selection(row),change),mode=Objects.equals(current,row.get("after_hash"))?"RESTORE_METADATA":"COMPENSATE_KEEP_FACTS";
        String hash=PlanReviewLegacyInventory.hash(List.of(change,current,mode,UUID.randomUUID().toString()));
        jdbc.update("UPDATE plan_review_legacy_change SET rollback_preview_hash=?,rollback_data_hash=?,rollback_expires_at=?,rollback_mode=?,rollback_approved_by=NULL,rollback_approved_at=NULL,update_time=? WHERE id=?",hash,current,now()+600_000,mode,now(),change);
        event(project,change,"LEGACY_ROLLBACK_PREVIEW",actor,mode,hash);
        return Map.of("projectId",project,"changeId",change,"previewHash",hash,"mode",mode,"preservesBusinessFacts",true);
    }
    public Map<String,Object> approveRollback(String project,String change,String previewHash,boolean acknowledged,String actor){
        owner(project,actor);var row=lock(project,change);requireState(row,"APPLIED");rollbackFresh(row,previewHash,acknowledged);
        jdbc.update("UPDATE plan_review_legacy_change SET rollback_approved_by=?,rollback_approved_at=?,update_time=? WHERE id=?",actor,now(),now(),change);event(project,change,"LEGACY_ROLLBACK_APPROVE",actor,"APPROVED",previewHash);return safe(lock(project,change));
    }
    public Map<String,Object> rollback(String project,String change,String previewHash,boolean acknowledged,String actor){
        executor(project,actor);var row=lock(project,change);
        if("ROLLED_BACK".equals(row.get("state"))&&Objects.equals(previewHash,row.get("rollback_preview_hash")))return safe(row);
        requireState(row,"APPLIED");rollbackFresh(row,previewHash,acknowledged);owner(project,(String)row.get("rollback_approved_by"));
        var choice=selection(row);if(hasAdoption(choice))adopter().stop(project,change);
        var owned=new ArrayList<>(jdbc.queryForList("SELECT DISTINCT relation_id,generation FROM plan_review_legacy_group_map WHERE project_id=? AND change_id=? AND created_relation=1",project,change));
        owned.addAll(jdbc.queryForList("SELECT DISTINCT relation_id,generation FROM plan_review_legacy_job_map WHERE project_id=? AND change_id=? AND created_relation=1",project,change));
        for(var relation:owned){
            jdbc.update("UPDATE plan_review_relation SET state='ENDED',sync_state='ENDED',active_plan_id=NULL,active_review_id=NULL,approval_pending=0,next_retry_at=NULL,ended_reason='LEGACY_ROLLBACK',update_time=? WHERE id=? AND generation=? AND project_id=? AND state='ACTIVE'",now(),relation.get("relation_id"),relation.get("generation"),project);
        }
        for(var relation:jdbc.queryForList("SELECT DISTINCT o.relation_id,o.generation FROM plan_review_operation o WHERE o.project_id=? AND o.source_kind='PLAN_SERVICE' AND o.operation_type='CREATE_PAIR' AND o.relation_id IS NOT NULL AND EXISTS(SELECT 1 FROM plan_review_legacy_job_map j WHERE j.project_id=? AND j.change_id=? AND o.request_key=CONCAT('import:',j.job_id)) FOR UPDATE",project,project,change)){
            jdbc.update("UPDATE plan_review_relation SET state='ENDED',sync_state='ENDED',active_plan_id=NULL,active_review_id=NULL,approval_pending=0,next_retry_at=NULL,ended_reason='LEGACY_ROLLBACK',update_time=? WHERE id=? AND generation=? AND project_id=? AND state='ACTIVE'",now(),relation.get("relation_id"),relation.get("generation"),project);
        }
        var originalData=map(backupData(row).get("data"));
        if("RESTORE_METADATA".equals(row.get("rollback_mode")))for(var group:rows(originalData,"groups"))if("KEEP".equals(choice.groups().get(group.get("id")))&&((Collection<?>)originalData.get("physicalGroups")).contains(group.get("id"))){
            if(!jdbc.queryForList("SELECT id FROM plan_review_relation WHERE state='ACTIVE' AND project_id=? AND (active_plan_id=? OR active_review_id=?) FOR UPDATE",project,group.get("plan_id"),group.get("review_id")).isEmpty())throw fail("ROLLBACK_CONFLICT","已存在新的关联代次，不会覆盖");
            insertOriginalGroup(group);
        }
        // Old jobs deliberately remain MIGRATED_V2: rollback cannot resurrect obsolete work.
        jdbc.update("UPDATE plan_review_legacy_group_map SET state='ROLLED_BACK' WHERE project_id=? AND change_id=? AND action='KEEP'",project,change);
        jdbc.update("UPDATE plan_review_legacy_change SET state='ROLLED_BACK',revision=revision+1,update_time=? WHERE id=?",now(),change);event(project,change,"LEGACY_ROLLBACK",actor,(String)row.get("rollback_mode"),previewHash);return safe(lock(project,change));
    }
    private void insertOriginalGroup(Map<String,Object> group){
        var columns=new TreeSet<>(group.keySet());if(!Set.of("id","project_id","plan_id","review_id","revision","create_user","create_time").containsAll(columns))throw fail("LEGACY_SCHEMA_CHANGED","旧关系结构变化，停止恢复元数据");
        jdbc.update("INSERT INTO functional_import_followup_group("+String.join(",",columns)+") VALUES("+String.join(",",Collections.nCopies(columns.size(),"?"))+")",columns.stream().map(group::get).toArray());
    }
    private void rollbackFresh(Map<String,Object> row,String previewHash,boolean acknowledged){
        if(!acknowledged||previewHash==null||!Objects.equals(previewHash,row.get("rollback_preview_hash")))throw fail("EXPLICIT_APPROVAL_REQUIRED","须批准准确的回退预览");
        if(row.get("rollback_expires_at")==null||((Number)row.get("rollback_expires_at")).longValue()<=now())throw fail("ROLLBACK_PREVIEW_EXPIRED","回退预览已过期，请重新预览并批准");
        if(!afterHash(selection(row),(String)row.get("id")).equals(row.get("rollback_data_hash")))throw fail("ROLLBACK_PREVIEW_STALE","回退预览后出现新写入，须重新判断并批准保留事实的补偿方案");
    }
    private String afterHash(PlanReviewLegacyDecision choice,String change){
        var data=new TreeMap<String,Object>(snapshots.read(choice,true,true));
        data.put("mappedRelations",jdbc.queryForList("SELECT r.* FROM plan_review_relation r WHERE r.project_id=? AND (r.id IN (SELECT relation_id FROM plan_review_legacy_group_map WHERE project_id=? AND change_id=?) OR r.id IN (SELECT relation_id FROM plan_review_legacy_job_map WHERE project_id=? AND change_id=?)) ORDER BY r.id FOR UPDATE",choice.projectId(),choice.projectId(),change,choice.projectId(),change));
        data.put("mappedOperations",jdbc.queryForList("SELECT o.* FROM plan_review_operation o WHERE o.project_id=? AND o.id IN (SELECT operation_id FROM plan_review_legacy_job_map WHERE project_id=? AND change_id=?) ORDER BY o.id FOR UPDATE",choice.projectId(),choice.projectId(),change));
        data.put("newWatermarks",jdbc.queryForList("SELECT i.* FROM plan_review_import_followup_item i WHERE i.job_id IN (SELECT job_id FROM plan_review_legacy_job_map WHERE project_id=? AND change_id=?) ORDER BY i.job_id,i.case_id FOR UPDATE",choice.projectId(),change));
        return PlanReviewLegacySnapshot.hash(data);
    }
    private void validatePairs(PlanReviewLegacyDecision choice,Map<String,Object> data){
        var plans=new HashMap<String,String>();var reviews=new HashMap<String,String>();
        for(var group:rows(data,"groups"))if("KEEP".equals(choice.groups().get(group.get("id")))){
            String plan=(String)group.get("plan_id"),review=(String)group.get("review_id");
            if(plan==null||review==null||plans.containsKey(plan)&&!review.equals(plans.get(plan))||reviews.containsKey(review)&&!plan.equals(reviews.get(review)))throw fail("EXPLICIT_CONFLICT_DECISION_REQUIRED","同一计划或评审仍存在多个保留目标，请明确隔离或解除冲突边");
            plans.put(plan,review);reviews.put(review,plan);
            if(rows(data,"unifiedRelations").stream().anyMatch(r->plan.equals(r.get("plan_id"))||review.equals(r.get("review_id"))))throw fail("MIGRATION_RELATION_OCCUPIED","原对象已有新协议关系记录，迁移不会重配已结束代次或替换当前关系，请先核查冲突");
            boolean p=rows(data,"plans").stream().anyMatch(v->plan.equals(v.get("id"))&&"TEST_PLAN".equals(v.get("type"))&&PlanReviewPolicy.State.parse((String)v.get("status")).editable());
            boolean r=rows(data,"reviews").stream().anyMatch(v->review.equals(v.get("id"))&&PlanReviewPolicy.State.parse((String)v.get("status")).editable());
            if(!p||!r)throw fail("LEGACY_PAIR_NOT_EDITABLE","原对象不可访问或未恢复可编辑；保留选择不会改配目标、自动解锁或补建已删除对象");
        }
        for(var job:rows(data,"jobs"))if("ADOPT".equals(choice.jobs().get(job.get("job_id")).action())&&job.get("review_id")!=null){
            String plan=(String)job.get("plan_id"),review=(String)job.get("review_id");
            if(plans.containsKey(plan)&&!review.equals(plans.get(plan))||reviews.containsKey(review)&&!plan.equals(reviews.get(review)))throw fail("EXPLICIT_CONFLICT_DECISION_REQUIRED","多个旧任务仍要占用同一计划或评审，请明确保留哪项、隔离或终止其余项");plans.put(plan,review);reviews.put(review,plan);
            var matching=rows(data,"groups").stream().filter(group->Objects.equals(group.get("plan_id"),job.get("plan_id"))&&Objects.equals(group.get("review_id"),job.get("review_id"))).toList();
            if(!matching.isEmpty()&&matching.stream().noneMatch(group->"KEEP".equals(choice.groups().get(group.get("id")))))throw fail("MIGRATION_DECISION_CONFLICT","同一旧边已选择隔离或解除，不能同时通过旧任务重新配对");
        }
    }
    private void executor(String project,String actor){
        var access=auth.current(actor,project,true);if(!access.permissions().contains(PlanReviewPolicy.Permission.LEGACY_MIGRATE)||!PlanReviewPolicy.allowed(PlanReviewPolicy.Action.PAIR,access))throw new PlanReviewFailure("PERMISSION_REQUIRED",403,"需要当前项目迁移、双方读更新权限及服务授权");store.lockProject(project);
    }
    private void owner(String project,String actor){
        var access=auth.current(actor,project,true);if(!access.permissions().containsAll(Set.of(PlanReviewPolicy.Permission.TAKEOVER_APPROVE,PlanReviewPolicy.Permission.PLAN_READ,PlanReviewPolicy.Permission.REVIEW_READ)))throw new PlanReviewFailure("OWNER_APPROVAL_REQUIRED",403,"需要当前项目负责人的明确批准");
    }
    private void rehearsalProof(Map<String,Object> row){
        if(row.get("backup_at")==null||row.get("rehearsal_id")==null||jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_legacy_rehearsal WHERE id=? AND project_id=? AND change_id=? AND snapshot_hash=? AND state='PASSED'",Integer.class,row.get("rehearsal_id"),row.get("project_id"),row.get("id"),row.get("snapshot_hash"))!=1)throw fail("REHEARSAL_REQUIRED","原备份尚无匹配的真实隔离演练凭证");
    }
    private void fresh(Map<String,Object> row,boolean includeApplied){
        if(((Number)row.get("expires_at")).longValue()<=now())throw fail("PREVIEW_EXPIRED","迁移预览已过期，请重新盘点并建立预览");
        if(!Objects.equals(row.get("snapshot_hash"),PlanReviewLegacySnapshot.hash(snapshots.read(selection(row),true,includeApplied))))throw fail("MIGRATION_SNAPSHOT_STALE","原对象、成员、意见引用或任务水位已变化，停止迁移并重新盘点");
    }
    private Map<String,Object> lock(String project,String change){
        var rows=jdbc.queryForList("SELECT * FROM plan_review_legacy_change WHERE project_id=? AND id=? FOR UPDATE",project,change);if(rows.size()!=1)throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"迁移记录不可访问");return rows.getFirst();
    }
    private PlanReviewLegacyJobAdopter adopter(){if(adopters.size()!=1)throw fail("LEGACY_JOB_ADOPTER_UNAVAILABLE","旧任务受控交接器不可用，停止迁移");return adopters.getFirst();}
    private static void requireState(Map<String,Object> row,String... states){if(!List.of(states).contains(row.get("state")))throw fail("MIGRATION_STAGE_CONFLICT","当前迁移阶段不允许此操作");}
    private static Map<String,Object> safe(Map<String,Object> row){var result=new TreeMap<String,Object>();for(String key:List.of("id","project_id","state","snapshot_hash","revision","backup_at","rehearsal_id","expires_at","rollback_mode"))if(row.get(key)!=null)result.put(key,row.get(key));return result;}
    private static Map<String,Object> result(Map<String,Object> row){return JSON.parseObject((String)row.get("result_json"),new TypeReference<>(){});}
    private static PlanReviewLegacyDecision selection(Map<String,Object> row){return JSON.parseObject((String)row.get("selection_json"),PlanReviewLegacyDecision.class);}
    private static Map<String,Object> backupData(Map<String,Object> row){return JSON.parseObject(new String((byte[])row.get("snapshot_json"),StandardCharsets.UTF_8),new TypeReference<>(){});}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object object){return (Map<String,Object>)object;}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Map<String,Object> object,String key){return (List<Map<String,Object>>)object.get(key);}
    private static String json(Object object){return JSON.toJSONString(object);}
    private static long now(){return System.currentTimeMillis();}
    private static boolean hasAdoption(PlanReviewLegacyDecision choice){return choice.jobs().values().stream().anyMatch(job->"ADOPT".equals(job.action()));}
    private void event(String project,String change,String action,String actor,String result,String hash){jdbc.update("INSERT INTO plan_review_audit(id,project_id,operation_id,action,actor,result,safe_reason,create_time) VALUES(?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),project,change,action,actor,result,"snapshot="+hash,now());}
    private static PlanReviewFailure fail(String code,String message){return new PlanReviewFailure(code,409,message);}
}
