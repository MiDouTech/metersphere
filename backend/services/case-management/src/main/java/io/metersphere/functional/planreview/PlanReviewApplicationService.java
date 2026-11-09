package io.metersphere.functional.planreview;

import io.metersphere.sdk.util.JSON;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** One relation authority for manual pairing and reconciliation. No source business writes here. */
@Service
@Transactional(rollbackFor = Exception.class)
public class PlanReviewApplicationService {
    private final JdbcTemplate jdbc;
    private final PlanReviewPersistence persistence;
    private final PlanReviewAuthorization authorization;
    private final TransactionTemplate tx;
    private final PlanReviewPlanStateResolver planStates;
    private List<PlanReviewReviewWriter> reviewWriters = List.of();
    private List<PlanReviewExecutionCredential> executionCredentials=List.of();
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setExecutionCredentials(List<PlanReviewExecutionCredential> credentials){executionCredentials=List.copyOf(credentials);}
    private String currentCredential(){
        var references=executionCredentials.stream().map(PlanReviewExecutionCredential::currentReference).filter(Objects::nonNull).distinct().toList();
        if(references.size()>1)throw fail("CREDENTIAL_CONTEXT_CONFLICT",403,"当前执行凭据上下文不明确");return references.isEmpty()?null:references.getFirst();
    }
    private void operationCredential(Map<String,Object> operation,String actor){
        String reference=currentCredential();
        if(!Objects.equals(reference,operation.get("executing_credential_id"))||reference!=null&&executionCredentials.stream().noneMatch(provider->provider.validateLinkedReview(reference,text(operation,"project_id"),actor)))throw fail("CREDENTIAL_CONTEXT_REQUIRED",403,"请使用原操作的当前有效凭据；未经批准不能替换为Web身份");
        if("AGENT".equals(operation.get("source_kind"))&&reference==null&&operation.get("approver")==null)throw fail("CREDENTIAL_CONTEXT_REQUIRED",403,"原Agent操作尚未批准转交Web执行");
    }

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setReviewWriters(List<PlanReviewReviewWriter> writers) { reviewWriters = List.copyOf(writers); }

    @org.springframework.beans.factory.annotation.Autowired
    public PlanReviewApplicationService(JdbcTemplate jdbc, PlanReviewPersistence persistence,
                                       PlanReviewAuthorization authorization, PlatformTransactionManager manager,
                                       PlanReviewPlanStateResolver planStates) {
        this.jdbc = jdbc;
        this.persistence = persistence;
        this.authorization = authorization;
        this.tx = new TransactionTemplate(manager);
        this.tx.setTimeout(30);
        this.planStates = planStates;
    }

    public Map<String, Object> summary(String side, String id, String actor) {
        boolean planSide = side(side);
        var object = object(planSide, id);
        String project = text(object, "project_id");
        authorization.requireRead(actor, project, planSide);
        var rows = jdbc.queryForList("SELECT * FROM plan_review_relation WHERE "
                + (planSide ? "active_plan_id" : "active_review_id") + "=? AND project_id=?", id, project);
        if (rows.isEmpty()) {
            boolean legacy = !PlanReviewLegacyOwnership.blockedIds(jdbc, planSide, List.of(id)).isEmpty();
            if (legacy) return Map.of("relationStatus", "NONE", "restricted", false, "legacyBlocked", true);
            return Map.of("relationStatus", "NONE", "restricted", false, "legacyBlocked", legacy,
                    "canPair", !legacy && state(object).editable()
                            && (!planSide || "TEST_PLAN".equals(object.get("type")))
                            && PlanReviewPolicy.allowed(PlanReviewPolicy.Action.PAIR, authorization.current(actor, project, false)));
        }
        var access = authorization.current(actor, project, false);
        if (!access.permissions().contains(planSide ? PlanReviewPolicy.Permission.REVIEW_READ : PlanReviewPolicy.Permission.PLAN_READ)) {
            return Map.of("relationStatus", "RESTRICTED", "restricted", true);
        }
        var relation = rows.getFirst();
        var target = object(!planSide, text(relation, planSide ? "review_id" : "plan_id"));
        var plan = planSide ? object : target;
        var review = planSide ? target : object;
        var result = new LinkedHashMap<String, Object>();
        result.put("relationStatus", "ACTIVE"); result.put("restricted", false);
        result.put("relationId", relation.get("id")); result.put("generation", relation.get("generation"));
        result.put("target", selected(target, "id", "num", "name", "status"));
        result.put("sync", syncSummary(relation, plan, review, access.serviceGrant()));
        result.put("memberEpochs", persistence.currentEpochs(text(relation, "review_id")));
        result.put("canUnlink", PlanReviewPolicy.allowed(PlanReviewPolicy.Action.UNLINK, access) && editable(plan, review));
        result.put("canReconfirm", PlanReviewPolicy.allowed(PlanReviewPolicy.Action.RETRY_SYNC, access)
                && editable(plan, review) && manualRecoveryAllowed(relation));
        result.put("scopeMessage", "仅同步有效功能用例；接口用例和场景不在本次评审范围");
        return result;
    }

    public void requireSourceEditable(String planId) {
        persistence.lockSource(planId);
        if (!state(object(true, planId)).editable()) throw fail("OBJECT_LOCKED", 409, "测试计划已完成、归档或状态待核实，不能维护成员");
    }

    /** Validate the entire selection before any source clock, history or defect cleanup is written. */
    public String requireSourceMemberSelection(String plan,String expectedProject,List<String> ids,String collection,String actor){
        var projects=jdbc.queryForList("SELECT project_id FROM test_plan WHERE id=?",String.class,plan);
        if(projects.size()!=1||expectedProject!=null&&!expectedProject.isBlank()&&!expectedProject.equals(projects.getFirst()))throw fail("RESOURCE_NOT_ACCESSIBLE",404,"所选计划或成员不可访问");
        String project=projects.getFirst();
        if(!authorization.current(actor,project,true).permissions().contains(PlanReviewPolicy.Permission.PLAN_MEMBERS))throw fail("PERMISSION_REQUIRED",403,"缺少当前计划成员维护权限");
        if(jdbc.queryForList("SELECT id FROM test_plan WHERE id=? AND project_id=? AND type='TEST_PLAN' FOR UPDATE",String.class,plan,project).size()!=1)throw fail("RESOURCE_NOT_ACCESSIBLE",404,"所选计划或成员不可访问");
        requireSourceEditable(plan);
        if(collection!=null&&!collection.isBlank()&&jdbc.queryForList("SELECT id FROM test_plan_collection WHERE id=? AND test_plan_id=? FOR UPDATE",String.class,collection,plan).size()!=1)throw fail("RESOURCE_NOT_ACCESSIBLE",404,"所选测试集不属于当前计划");
        if(ids==null||ids.stream().anyMatch(id->id==null||id.isBlank())||new HashSet<>(ids).size()!=ids.size())throw fail("INVALID_SELECTION",400,"请选择不重复的有效成员");
        var ordered=new ArrayList<>(ids);Collections.sort(ordered);int found=0;
        for(int start=0;start<ordered.size();start+=200){
            var batch=ordered.subList(start,Math.min(start+200,ordered.size()));var args=new ArrayList<Object>();args.add(plan);args.addAll(batch);
            String scope="";if(collection!=null&&!collection.isBlank()){scope=" AND test_plan_collection_id=?";args.add(collection);}
            found+=jdbc.queryForList("SELECT id FROM test_plan_functional_case WHERE test_plan_id=? AND id IN ("+String.join(",",Collections.nCopies(batch.size(),"?"))+")"+scope+" ORDER BY id FOR UPDATE",String.class,args.toArray()).size();
        }
        if(found!=ids.size())throw fail("RESOURCE_NOT_ACCESSIBLE",404,"选择包含其他计划、测试集或已失效成员，未执行任何删除");
        return project;
    }

    /** Original opinion pipeline may recompute current statistics, but never replay old deltas. */
    public boolean refreshOpinionStatistics(String reviewId) {
        persistence.lockReview(reviewId);
        var rows = jdbc.queryForList("SELECT * FROM plan_review_relation WHERE active_review_id=? AND state='ACTIVE' FOR UPDATE", reviewId);
        if (rows.isEmpty()) {
            var review=jdbc.queryForMap("SELECT * FROM case_review WHERE id=? FOR UPDATE",reviewId);
            if(!state(review).editable())return true;
            var counts=statusCounts(jdbc.queryForList("SELECT m.status FROM case_review_functional_case m JOIN functional_case c ON CAST(c.id AS BINARY(50))=CAST(m.case_id AS BINARY(50)) WHERE m.review_id=? AND c.project_id=? AND c.deleted=0 FOR UPDATE",String.class,reviewId,review.get("project_id")));
            long total=number(counts,"total"),passed=number(counts,"passed");
            var rate=total==0?java.math.BigDecimal.ZERO:java.math.BigDecimal.valueOf(passed*100).divide(java.math.BigDecimal.valueOf(total),2,java.math.RoundingMode.HALF_UP);
            String status=number(counts,"pending")==total?"PREPARED":passed+number(counts,"failed")==total?"COMPLETED":"UNDERWAY";
            jdbc.update("UPDATE case_review SET case_count=?,pass_rate=? WHERE id=?",total,rate,reviewId);
            if("COMPLETED".equals(status))persistence.freezeIndependentReview(reviewId,status,"plan-review-statistics");
            jdbc.update("UPDATE case_review SET status=? WHERE id=?",status,reviewId);
            return true;
        }
        var initial = rows.getFirst();
        var context = lockPair(text(initial, "plan_id"), reviewId);
        var current = currentRelation(text(initial, "id"));
        if (!"ACTIVE".equals(current.get("state"))) return true;
        if (!state(context.review).editable()) return true; // immutable completed/archived snapshot
        recount(reviewId);
        if ("SYNCED".equals(current.get("sync_state"))
                && number(current, "applied_version") == number(current, "source_version")) {
            var counts = statusCounts(jdbc.queryForList("SELECT status FROM case_review_functional_case WHERE review_id=? FOR UPDATE",String.class,reviewId));
            long total = number(counts, "total"), pending = number(counts, "pending"), finished = number(counts, "finished");
            String status = pending == total ? "PREPARED" : finished == total ? "COMPLETED" : "UNDERWAY";
            if ("COMPLETED".equals(status)) persistence.freeze(current, "review", reviewId, status,
                    new PlanReviewFingerprintReader(jdbc).read(reviewId, PlanReviewFingerprintReader.ReadMode.LOCKED, PlanReviewFingerprintReader.Format.COMPLETION_V1),
                    "plan-review-statistics",planStates.statisticsSnapshot(text(current,"plan_id")));
            jdbc.update("UPDATE case_review SET status=? WHERE id=?", status, reviewId);
        }
        return true;
    }

    public Map<String, Long> opinionEpochs(String review, String actor) {
        var target = object(false, review);
        authorization.requireRead(actor, text(target, "project_id"), false);
        return persistence.currentEpochs(review);
    }

    public Map<String, Object> candidates(String side, String id, String keyword, int page, int size, String actor) {
        boolean planSide = side(side);
        page(page, size);
        if (keyword == null) keyword = "";
        if (keyword.length() > 255) throw fail("INVALID_FIELD", 400, "搜索内容过长");
        var current = object(planSide, id);
        String project = text(current, "project_id");
        authorization.require(PlanReviewPolicy.Action.UNLINK, actor, project); // M02, without SG requirement for viewing options
        if (!state(current).editable()) throw fail("OBJECT_LOCKED", 409, "当前对象不可编辑");
        String table = planSide ? "case_review" : "test_plan";
        String activeColumn = planSide ? "active_review_id" : "active_plan_id";
        String where = " o.project_id=? AND o.status IN (" + (planSide ? "'PREPARED','UNDERWAY'" : "'PREPARED','UNDERWAY','NOT_ARCHIVED'") + ") "
                + (planSide ? "" : " AND o.type='TEST_PLAN' ")
                + " AND NOT EXISTS(SELECT 1 FROM plan_review_relation r WHERE r." + activeColumn + "=o.id AND r.project_id=o.project_id)"
                + " AND NOT EXISTS(SELECT 1 FROM plan_review_operation op WHERE op." + (planSide ? "review_id" : "plan_id")
                + "=o.id AND op.state IN ('PENDING','BLOCKED_PERMISSION','RETRYABLE_FAILURE'))"
                + " AND NOT EXISTS(SELECT 1 FROM functional_import_followup_group lg WHERE CAST(lg."+(planSide?"review_id":"plan_id")+" AS BINARY(50))=CAST(o.id AS BINARY(50)))"
                + " AND NOT EXISTS(SELECT 1 FROM functional_import_followup lf WHERE lf.policy<>'IMPORT_ONLY' AND lf.state<>'MIGRATED_V2' AND CAST(lf."+(planSide?"review_id":"plan_id")+" AS BINARY(50))=CAST(o.id AS BINARY(50)))"
                + " AND (o.name LIKE ? ESCAPE '!' OR CAST(o.num AS CHAR(32)) LIKE ? ESCAPE '!')";
        String query = "%" + keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        if (!planSide) {
            var all = jdbc.queryForList("SELECT o.* FROM test_plan o WHERE " + where + " ORDER BY o.update_time DESC,o.id", project, query, query)
                    .stream().map(planStates::resolve).filter(row -> state(row).editable())
                    .map(row -> selected(row, "id", "num", "name", "status")).toList();
            int from = (int) Math.min((page - 1L) * size, all.size());
            return Map.of("items", all.subList(from, Math.min(from + size, all.size())), "total", all.size(), "page", page, "size", size);
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " o WHERE " + where, Long.class, project, query, query);
        var items = jdbc.queryForList("SELECT o.id,o.num,o.name,o.status FROM " + table + " o WHERE " + where
                + " ORDER BY o.update_time DESC,o.id LIMIT ? OFFSET ?", project, query, query, size, (page - 1L) * size);
        return Map.of("items", items, "total", count == null ? 0 : count, "page", page, "size", size);
    }

    public Map<String, Object> preview(String planId, String reviewId, String purpose, String actor) {
        if (purpose == null || !Set.of("PAIR_EXISTING", "RECONFIRM", "CATCH_UP").contains(purpose)) throw fail("INVALID_PURPOSE", 400, "预览用途无效");
        String project = text(object(true, planId), "project_id");
        boolean first = purpose.equals("PAIR_EXISTING");
        authorization.require(first ? PlanReviewPolicy.Action.PAIR : PlanReviewPolicy.Action.RETRY_SYNC, actor, project);
        var context = lockPair(planId, reviewId);
        if(first)persistence.requireNoLegacyOwnership(project,planId,reviewId);
        authorization.require(first ? PlanReviewPolicy.Action.PAIR : PlanReviewPolicy.Action.RETRY_SYNC, actor, project);
        requireEditable(context.plan, context.review);
        var occupied = occupied(planId, reviewId, project);
        if (first && !occupied.isEmpty()) throw fail("RELATION_OCCUPIED", 409, "对象已关联，请先解除原关系");
        if (!first && (occupied.size() != 1 || !planId.equals(occupied.getFirst().get("plan_id"))
                || !reviewId.equals(occupied.getFirst().get("review_id")))) throw fail("RELATION_CHANGED", 409, "关联已变化，请刷新");
        var relation = first ? null : currentRelation(text(occupied.getFirst(), "id"));
        if (!first) requireManualRecovery(relation);
        var source = persistence.sourceChanged(planId);
        var difference = difference(reviewId, source, first);
        String generation = first ? uuid() : text(relation, "generation");
        long opinion = first ? 0 : number(relation, "opinion_revision");
        String id = uuid(), fingerprint = fingerprint(reviewId);
        long now = now();
        jdbc.update("""
                INSERT INTO plan_review_preview(id,project_id,purpose,plan_id,review_id,generation,source_version,
                opinion_revision,grant_revision,plan_revision,review_revision,target_fingerprint,difference_hash,
                issued_at,expires_at,actor) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, project, purpose, planId, reviewId, generation, source.version(), opinion,
                number(context.grant, "revision"), number(context.plan, "update_time"), number(context.review, "update_time"),
                fingerprint, hash(JSON.toJSONString(difference)), now, now + PlanReviewPolicy.PREVIEW_TTL.toMillis(), actor);
        return Map.of("previewId", id, "purpose", purpose, "generation", generation, "sourceVersion", source.version(),
                "expiresAt", now + PlanReviewPolicy.PREVIEW_TTL.toMillis(), "difference", difference,
                "message", "以测试计划为准；移出项意见保留为只读历史，重新加入须重新评审；不会修改测试计划");
    }

    public Map<String,Object> previewNewReview(String plan, String requestKey, Map<String,Object> draft, String sourceKind, String actor) {
        key(requestKey);
        if (draft==null || sourceKind==null || !Set.of("WEB","AGENT").contains(sourceKind)) throw fail("INVALID_FIELD",400,"创建参数或来源无效");
        String project=text(object(true,plan),"project_id");
        authorization.require(PlanReviewPolicy.Action.CREATE_LINKED_REVIEW,actor,project);
        persistence.lockSource(plan);
        persistence.requireNoLegacyOwnership(project,plan,null);
        authorization.require(PlanReviewPolicy.Action.CREATE_LINKED_REVIEW,actor,project);
        if (!state(object(true,plan)).editable()) throw fail("OBJECT_LOCKED",409,"测试计划不可编辑");
        if (!project.equals(draft.get("projectId"))) throw fail("RESOURCE_NOT_ACCESSIBLE",404,"对象不可访问");
        validateReviewDraft(project,draft);
        if (!jdbc.queryForList("SELECT id FROM plan_review_relation WHERE active_plan_id=?",plan).isEmpty()) throw fail("RELATION_OCCUPIED",409,"测试计划已被关联");
        var canonical=new TreeMap<>(draft); String payload=JSON.toJSONString(canonical), payloadHash=hash(payload);
        var previous=jdbc.queryForList("SELECT * FROM plan_review_operation WHERE project_id=? AND source_kind=? AND operation_type='CREATE_REVIEW' AND request_key=? FOR UPDATE",project,sourceKind,requestKey);
        String operation,review,generation;
        if(previous.isEmpty()){
            String reference=currentCredential();
            if("AGENT".equals(sourceKind)&&reference==null)throw fail("CREDENTIAL_CONTEXT_REQUIRED",403,"Agent创建预览需要当前有效凭据");
            if(reference!=null&&executionCredentials.stream().noneMatch(provider->provider.validateLinkedReview(reference,project,actor)))throw fail("CREDENTIAL_CONTEXT_REQUIRED",403,"当前Agent凭据已失效或作用域不足");
            operation=uuid(); review=uuid(); generation=uuid();
            jdbc.update("INSERT INTO plan_review_operation(id,project_id,source_kind,operation_type,request_key,payload_hash,request_json,original_actor,executing_actor,plan_id,review_id,generation,stage,state,create_time,update_time) VALUES(?,?,?,'CREATE_REVIEW',?,?,?,?,?,?,?,?,'VALIDATED','PENDING',?,?)",
                    operation,project,sourceKind,requestKey,payloadHash,payload,actor,actor,plan,review,generation,now(),now());
            jdbc.update("UPDATE plan_review_operation SET original_credential_id=?,executing_credential_id=? WHERE id=?",reference,reference,operation);
        }else{
            var old=previous.getFirst();
            if(!actor.equals(old.get("executing_actor")))throw fail("RESOURCE_NOT_ACCESSIBLE",404,"操作不可访问");
            operationCredential(old,actor);
            if("TERMINATED".equals(old.get("state")))throw fail("OPERATION_TERMINATED",409,"原创建操作已终止，不会重新预览或创建对象");
            if(!payloadHash.equals(old.get("payload_hash"))||!plan.equals(old.get("plan_id")))throw fail("REQUEST_KEY_CONFLICT",409,"同一请求标识不能用于不同创建参数");
            if(!"VALIDATED".equals(old.get("stage")))throw fail("OPERATION_ALREADY_CONFIRMED",409,"原操作已确认或终止，请查询原操作");
            operation=text(old,"id"); review=text(old,"review_id"); generation=text(old,"generation");
        }
        var source=persistence.sourceChanged(plan); String preview=uuid(); var currentPlan=object(true,plan);
        long grant=jdbc.queryForObject("SELECT revision FROM plan_review_grant WHERE project_id=?",Long.class,project);
        jdbc.update("INSERT INTO plan_review_preview(id,project_id,purpose,plan_id,review_id,generation,source_version,opinion_revision,grant_revision,plan_revision,review_revision,target_fingerprint,difference_hash,issued_at,expires_at,actor) VALUES(?,?,'CREATE_LINKED_REVIEW',?,?,?,?,0,?,?,0,?,?,?,?,?)",
                preview,project,plan,review,generation,source.version(),grant,number(currentPlan,"update_time"),payloadHash,operation,now(),now()+600_000,actor);
        return Map.of("previewId",preview,"operationId",operation,"generation",generation,"sourceVersion",source.version(),"expiresAt",now()+600_000,
                "difference",Map.of("added",source.present(),"removed",List.of(),"retained",List.of(),"reentered",List.of()),
                "message","确认后创建评审并关联所选计划；只同步有效功能用例，接口和场景不在评审范围。取消不会创建评审。");
    }

    public Map<String,Object> recoverNewReview(String project,String operationId,String actor){
        authorization.require(PlanReviewPolicy.Action.CREATE_LINKED_REVIEW,actor,project);
        var rows=jdbc.queryForList("SELECT * FROM plan_review_operation WHERE id=? AND project_id=? AND operation_type='CREATE_REVIEW' AND source_kind IN ('WEB','AGENT') FOR UPDATE",operationId,project);
        if(rows.size()!=1||!actor.equals(rows.getFirst().get("executing_actor")))throw fail("RESOURCE_NOT_ACCESSIBLE",404,"原新评审创建操作不可访问");
        var operation=rows.getFirst();operationCredential(operation,actor);
        if("TERMINATED".equals(operation.get("state")))throw fail("OPERATION_TERMINATED",409,"原创建操作已终止");
        if(!"VALIDATED".equals(operation.get("stage")))throw fail("OPERATION_ALREADY_CONFIRMED",409,"原评审已确认创建，成员恢复请另行预览");
        var draft=JSON.parseObject(text(operation,"request_json"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        var preview=new LinkedHashMap<>(previewNewReview(text(operation,"plan_id"),text(operation,"request_key"),draft,text(operation,"source_kind"),actor));
        preview.put("requestKey",operation.get("request_key"));preview.put("planId",operation.get("plan_id"));preview.put("projectId",project);
        preview.put("settingsSummary",Map.of("name",draft.get("name"),"reviewPassRule",draft.get("reviewPassRule"),"reviewerCount",((List<?>)draft.get("reviewers")).size()));
        return preview;
    }
    public Map<String,Object> newReviewCandidates(String project,String keyword,int page,int size,String actor){
        page(page,size);
        if(!authorization.current(actor,project,false).permissions().containsAll(PlanReviewPolicy.required(PlanReviewPolicy.Action.CREATE_LINKED_REVIEW)))throw fail("PERMISSION_REQUIRED",403,"缺少选择关联计划的权限");
        String query="%"+Objects.toString(keyword,"").strip()+"%";
        var rows=jdbc.queryForList("SELECT p.* FROM test_plan p WHERE p.project_id=? AND p.type='TEST_PLAN' AND (p.name LIKE ? OR CAST(p.num AS CHAR) LIKE ?) AND NOT EXISTS(SELECT 1 FROM plan_review_relation r WHERE r.active_plan_id=p.id) AND NOT EXISTS(SELECT 1 FROM functional_import_followup_group lg WHERE CAST(lg.plan_id AS BINARY(50))=CAST(p.id AS BINARY(50))) AND NOT EXISTS(SELECT 1 FROM functional_import_followup lf WHERE lf.policy<>'IMPORT_ONLY' AND CAST(lf.plan_id AS BINARY(50))=CAST(p.id AS BINARY(50))) ORDER BY p.update_time DESC,p.id",project,query,query)
                .stream().map(planStates::resolve).filter(row->state(row).editable()).map(row->selected(row,"id","num","name","status")).toList();
        int from=(int)Math.min((page-1L)*size,rows.size());
        return Map.of("items",rows.subList(from,Math.min(from+size,rows.size())),"total",rows.size(),"page",page,"size",size);
    }

    public Map<String,Object> confirmNewReview(String previewId,String requestKey,boolean acknowledged,String actor) {
        key(requestKey); if(!acknowledged)throw fail("ACK_REQUIRED",400,"请确认评审范围与创建影响");
        var rows=jdbc.queryForList("SELECT * FROM plan_review_preview WHERE id=? AND purpose='CREATE_LINKED_REVIEW' AND actor=?",previewId,actor);
        if(rows.size()!=1)throw fail("RESOURCE_NOT_ACCESSIBLE",404,"预览不可访问");
        var preview=rows.getFirst(); String project=text(preview,"project_id"),plan=text(preview,"plan_id");
        authorization.require(PlanReviewPolicy.Action.CREATE_LINKED_REVIEW,actor,project); persistence.lockSource(plan);
        persistence.requireNoLegacyOwnership(project,plan,null);
        authorization.require(PlanReviewPolicy.Action.CREATE_LINKED_REVIEW,actor,project);
        var operation=jdbc.queryForMap("SELECT * FROM plan_review_operation WHERE id=? AND project_id=? FOR UPDATE",preview.get("difference_hash"),project);
        if(!actor.equals(operation.get("executing_actor")))throw fail("RESOURCE_NOT_ACCESSIBLE",404,"操作不可访问");
        operationCredential(operation,actor);
        if(!requestKey.equals(operation.get("request_key")))throw fail("REQUEST_KEY_CONFLICT",409,"请使用原创建请求标识");
        if("TERMINATED".equals(operation.get("state")))throw fail("OPERATION_TERMINATED",410,"原创建操作已结束，请查询原操作");
        if(operation.get("relation_id")!=null)return operationResult(operation);
        if(!"VALIDATED".equals(operation.get("stage")))throw fail("OPERATION_TERMINATED",409,"创建操作不可继续");
        if(now()>=number(preview,"expires_at"))throw fail("PREVIEW_EXPIRED",410,"预览已过期，请重新预览");
        var currentPlan=object(true,plan); var source=persistence.sourceChanged(plan);
        long grant=jdbc.queryForObject("SELECT revision FROM plan_review_grant WHERE project_id=?",Long.class,project);
        if(!state(currentPlan).editable()||number(currentPlan,"update_time")!=number(preview,"plan_revision")||source.version()!=number(preview,"source_version")||grant!=number(preview,"grant_revision"))throw fail("PREVIEW_STALE",409,"计划或授权已变化，请重新预览");
        if(!jdbc.queryForList("SELECT id FROM plan_review_relation WHERE active_plan_id=?",plan).isEmpty())throw fail("RELATION_OCCUPIED",409,"测试计划已被关联");
        var draft=JSON.parseObject(text(operation,"request_json"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}); validateReviewDraft(project,draft);
        if(reviewWriters.size()!=1)throw fail("CREATION_UNAVAILABLE",503,"评审创建服务暂不可用");
        reviewWriters.getFirst().create(draft,text(operation,"review_id"),actor);
        linkCreatedPair(text(operation,"id"),actor);
        var created=jdbc.queryForMap("SELECT * FROM plan_review_operation WHERE id=?",operation.get("id"));
        jdbc.update("UPDATE plan_review_relation SET approval_pending=1,approval_id=?,approved_source_version=?,approved_opinion_revision=opinion_revision,approved_grant_revision=?,approved_target_fingerprint=? WHERE id=? AND generation=?",
                operation.get("id"),source.version(),grant,fingerprint(text(operation,"review_id")),created.get("relation_id"),created.get("generation"));
        jdbc.update("UPDATE plan_review_preview SET consumed_by=? WHERE id=?",operation.get("id"),previewId);
        return operationResult(created);
    }

    private void validateReviewDraft(String project,Map<String,Object> draft){
        if(!Set.of("projectId","name","moduleId","reviewPassRule","reviewers","startTime","endTime","tags","description").containsAll(draft.keySet()))throw fail("INVALID_FIELD",400,"关联评审不接受独立成员或预留对象参数");
        PlanReviewSettings settings;
        try { settings=JSON.parseObject(JSON.toJSONString(draft),PlanReviewSettings.class); }
        catch(RuntimeException invalid){throw fail("REVIEW_SETTINGS_INVALID",400,"请核对评审名称、目录、评审人和通过规则");}
        if(!"root".equals(settings.moduleId()) && jdbc.queryForList("SELECT id FROM case_review_module WHERE id=? AND project_id=? FOR UPDATE",String.class,settings.moduleId(),project).size()!=1)throw fail("REVIEW_SETTINGS_INVALID",400,"评审目录已失效");
        for(String reviewer:settings.reviewers())if(!authorization.current(reviewer,project,true).permissions().contains(PlanReviewPolicy.Permission.REVIEW_OPINION))throw fail("REVIEW_SETTINGS_INVALID",400,"评审人已失效或无评审权限");
    }

    public Map<String, Object> confirm(String previewId, String requestKey, boolean acknowledged, String actor) {
        key(requestKey);
        if (!acknowledged) throw fail("ACK_REQUIRED", 400, "请先确认成员及历史影响");
        var previews = jdbc.queryForList("SELECT * FROM plan_review_preview WHERE id=?", previewId);
        if (previews.size() != 1) throw fail("RESOURCE_NOT_ACCESSIBLE", 404, "预览不可访问");
        var preview = previews.getFirst();
        if (!actor.equals(preview.get("actor"))) throw fail("RESOURCE_NOT_ACCESSIBLE", 404, "预览不可访问");
        String planId = text(preview, "plan_id"), reviewId = text(preview, "review_id"), project = text(preview, "project_id");
        boolean first = "PAIR_EXISTING".equals(preview.get("purpose"));
        authorization.require(first ? PlanReviewPolicy.Action.PAIR : PlanReviewPolicy.Action.RETRY_SYNC, actor, project);
        var context = lockPair(planId, reviewId);
        authorization.require(first ? PlanReviewPolicy.Action.PAIR : PlanReviewPolicy.Action.RETRY_SYNC, actor, project);
        var old = jdbc.queryForList("SELECT * FROM plan_review_operation WHERE project_id=? AND source_kind='WEB' AND operation_type='CONFIRM' AND request_key=? FOR UPDATE", project, requestKey);
        String payloadHash = hash(previewId);
        if (!old.isEmpty()) {
            if (!actor.equals(old.getFirst().get("original_actor"))) throw fail("RESOURCE_NOT_ACCESSIBLE", 404, "操作不可访问");
            if (!payloadHash.equals(old.getFirst().get("payload_hash"))) throw fail("REQUEST_KEY_CONFLICT", 409, "同一请求标识不能用于不同操作");
            return operationResult(old.getFirst());
        }
        preview = jdbc.queryForMap("SELECT * FROM plan_review_preview WHERE id=? FOR UPDATE", previewId);
        if(first)persistence.requireNoLegacyOwnership(project,planId,reviewId);
        if (preview.get("consumed_by") != null) throw fail("PREVIEW_CONSUMED", 409, "该预览已确认，请查询原操作");
        if (now() >= number(preview, "expires_at")) throw fail("PREVIEW_EXPIRED", 410, "预览已过期，请重新预览");
        requireEditable(context.plan, context.review);
        var active = occupied(planId, reviewId, project);
        Map<String, Object> current = active.isEmpty() ? null : active.getFirst();
        if (first && !active.isEmpty() || !first && (active.size() != 1 || !Objects.equals(preview.get("generation"), current.get("generation")))) {
            throw fail("RELATION_CHANGED", 409, "关系已变化，请重新预览");
        }
        if (!first) requireManualRecovery(current);
        var source = persistence.sourceChanged(planId);
        if (source.version() != number(preview, "source_version")
                || number(context.grant, "revision") != number(preview, "grant_revision")
                || number(context.plan, "update_time") != number(preview, "plan_revision")
                || number(context.review, "update_time") != number(preview, "review_revision")
                || !fingerprint(reviewId).equals(preview.get("target_fingerprint"))
                || !first && number(current, "opinion_revision") != number(preview, "opinion_revision")) {
            throw fail("PREVIEW_STALE", 409, "数据已变化，请重新预览");
        }
        String operation = uuid(), relationId = first ? uuid() : text(current, "id");
        String generation = text(preview, "generation");
        if (first) {
            try {
                jdbc.update("""
                        INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,active_plan_id,
                        active_review_id,state,sync_state,source_version,source_changed_at,original_actor,create_time,update_time)
                        VALUES(?,?,?,?,?,?,?,'ACTIVE','PENDING',?,?,?,?,?)
                        """, relationId, project, planId, reviewId, generation, planId, reviewId, source.version(), now(), actor, now(), now());
            } catch (DuplicateKeyException conflict) { throw fail("RELATION_OCCUPIED", 409, "对象已被其他请求关联，请刷新"); }
            initializeTarget(reviewId, source);
        }
        jdbc.update("""
                UPDATE plan_review_relation SET approved_source_version=?,approved_opinion_revision=opinion_revision,
                approved_grant_revision=?,approved_target_fingerprint=?,approval_pending=1,approval_id=?,
                sync_state='PENDING',attempts=0,retry_round=retry_round+1,next_retry_at=?,safe_error=NULL,update_time=?
                WHERE id=? AND generation=? AND state='ACTIVE'
                """, source.version(), number(context.grant, "revision"), fingerprint(reviewId), operation, now(), now(), relationId, generation);
        jdbc.update("UPDATE plan_review_preview SET consumed_by=? WHERE id=? AND consumed_by IS NULL", operation, previewId);
        jdbc.update("""
                INSERT INTO plan_review_operation(id,project_id,source_kind,operation_type,request_key,payload_hash,
                request_json,original_actor,executing_actor,plan_id,review_id,relation_id,generation,stage,state,create_time,update_time)
                VALUES(?,?,'WEB','CONFIRM',?,?,?,?,?,?,?,?,?,'TARGET_PENDING','PENDING',?,?)
                """, operation, project, requestKey, payloadHash, JSON.toJSONString(Map.of("previewId", previewId)), actor, actor,
                planId, reviewId, relationId, generation, now(), now());
        audit(project, relationId, generation, operation, "CONFIRM", actor, source.version(), 0, 0, "PENDING", null);
        return Map.of("operationId", operation, "relationId", relationId, "generation", generation, "stage", "TARGET_PENDING", "syncState", "PENDING");
    }

    /** Read-only progress must never allocate a preview, approve work or reset a retry budget. */
    public Map<String, Object> readOnlyDifference(String side, String id, String generation, String kind, int page, int size, String actor) {
        page(page, size);
        if (!Set.of("added", "removed", "retained").contains(kind)) throw fail("INVALID_FIELD", 400, "差异分类无效");
        var summary = summary(side, id, actor);
        if (Boolean.TRUE.equals(summary.get("restricted"))) throw fail("PERMISSION_REQUIRED", 403, "无权查看关联差异");
        if (generation == null || !generation.equals(summary.get("generation"))) throw fail("RELATION_CHANGED", 409, "关联已变化，请重新选择");
        var relation = jdbc.queryForMap("SELECT * FROM plan_review_relation WHERE id=?", summary.get("relationId"));
        String plan = text(relation, "plan_id"), review = text(relation, "review_id");
        // Same project -> plan -> review -> relation lock order, without INSERT IGNORE on a read path.
        jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE", relation.get("project_id"));
        jdbc.queryForList("SELECT id FROM test_plan WHERE id=? FOR UPDATE", plan);
        jdbc.queryForList("SELECT id FROM case_review WHERE id=? FOR UPDATE", review);
        relation = currentRelation(text(relation, "id"));
        if (!"ACTIVE".equals(relation.get("state")) || !generation.equals(relation.get("generation"))) throw fail("RELATION_CHANGED", 409, "关联已变化，请重新选择");
        authorization.requireRead(actor, text(relation, "project_id"), true);
        authorization.requireRead(actor, text(relation, "project_id"), false);
        var epochs = new HashMap<String, Long>();
        var present = new HashSet<String>();
        for (var row : jdbc.queryForList("SELECT case_id,epoch,present FROM plan_review_source_member WHERE plan_id=?", plan)) {
            String member = text(row, "case_id"); epochs.put(member, number(row, "epoch"));
            if (truth(row.get("present"))) present.add(member);
        }
        var effective = jdbc.queryForList("SELECT m.functional_case_id FROM test_plan_functional_case m JOIN functional_case c ON CAST(c.id AS BINARY(50))=CAST(m.functional_case_id AS BINARY(50)) WHERE m.test_plan_id=? AND c.project_id=? AND c.deleted=0", String.class, plan, relation.get("project_id"));
        var source = new PlanReviewSourceMembership(number(relation, "source_version"), present, epochs).changed(number(relation, "source_version"), effective);
        var delta = difference(review, source, false);
        var ids = new TreeSet<>(switch (kind) { case "added" -> delta.added(); case "removed" -> delta.removed(); default -> delta.retained(); });
        if (!"retained".equals(kind)) ids.addAll(delta.reentered());
        var ordered = ids.stream().toList();
        int from = (int) Math.min((page - 1L) * size, ordered.size());
        var items = new ArrayList<Map<String, Object>>();
        for (String member : ordered.subList(from, Math.min(from + size, ordered.size()))) {
            var cases = jdbc.queryForList("SELECT id,num,name FROM functional_case WHERE id=? AND project_id=?", member, relation.get("project_id"));
            var item = cases.isEmpty() ? new LinkedHashMap<String, Object>(Map.of("id", member, "name", "已移除用例")) : new LinkedHashMap<>(cases.getFirst());
            item.put("reentered", delta.reentered().contains(member)); items.add(item);
        }
        return Map.of("readonly", true, "generation", generation, "items", items, "total", ids.size());
    }

    private static boolean manualRecoveryAllowed(Map<String, Object> relation) {
        return "ACTIVE".equals(relation.get("state"))
                && Set.of("FAILED_MANUAL", "RECONFIRM_REQUIRED").contains(text(relation, "sync_state"));
    }

    private static void requireManualRecovery(Map<String, Object> relation) {
        if (!manualRecoveryAllowed(relation))
            throw fail("MANUAL_RECOVERY_NOT_APPLICABLE", 409, "当前同步状态不允许人工恢复，请查看进度；自动预算保持不变");
    }

    public Map<String, Object> previewItems(String previewId, String kind, int page, int size, String actor) {
        page(page, size);
        if (kind == null || !Set.of("added", "removed", "retained").contains(kind)) throw fail("INVALID_FIELD", 400, "差异分类无效");
        var rows = jdbc.queryForList("SELECT * FROM plan_review_preview WHERE id=? AND actor=?", previewId, actor);
        if (rows.size() != 1) throw fail("RESOURCE_NOT_ACCESSIBLE", 404, "预览不可访问");
        var preview = rows.getFirst();
        if("CREATE_LINKED_REVIEW".equals(preview.get("purpose")))return newReviewPreviewItems(preview,kind,page,size,actor);
        String project = text(preview, "project_id"), plan = text(preview, "plan_id"), review = text(preview, "review_id");
        boolean first = "PAIR_EXISTING".equals(preview.get("purpose"));
        authorization.require(first ? PlanReviewPolicy.Action.PAIR : PlanReviewPolicy.Action.RETRY_SYNC, actor, project);
        var context = lockPair(plan, review);
        if (now() >= number(preview, "expires_at")) throw fail("PREVIEW_EXPIRED", 410, "预览已过期，请重新预览");
        var source = persistence.sourceChanged(plan);
        var delta = difference(review, source, first);
        if (source.version() != number(preview, "source_version")
                || number(context.grant, "revision") != number(preview, "grant_revision")
                || !fingerprint(review).equals(preview.get("target_fingerprint"))
                || !hash(JSON.toJSONString(delta)).equals(preview.get("difference_hash"))) {
            throw fail("PREVIEW_STALE", 409, "成员或意见已变化，请重新预览");
        }
        var ids = new TreeSet<>(switch (kind) { case "added" -> delta.added(); case "removed" -> delta.removed(); default -> delta.retained(); });
        if (!"retained".equals(kind)) ids.addAll(delta.reentered());
        var ordered = ids.stream().toList();
        int from = (int) Math.min((page - 1L) * size, ordered.size());
        var items = new ArrayList<Map<String, Object>>();
        for (String id : ordered.subList(from, Math.min(from + size, ordered.size()))) {
            var cases = jdbc.queryForList("SELECT id,num,name FROM functional_case WHERE id=? AND project_id=?", id, project);
            var item = cases.isEmpty() ? new LinkedHashMap<String, Object>(Map.of("id", id, "name", "已移除用例")) : new LinkedHashMap<>(cases.getFirst());
            item.put("reentered", delta.reentered().contains(id)); items.add(item);
        }
        return Map.of("items", items, "total", ids.size(), "page", page, "size", size);
    }

    private Map<String,Object> newReviewPreviewItems(Map<String,Object> preview,String kind,int page,int size,String actor){
        String project=text(preview,"project_id"),plan=text(preview,"plan_id");
        authorization.require(PlanReviewPolicy.Action.CREATE_LINKED_REVIEW,actor,project); persistence.lockSource(plan);
        var source=persistence.sourceChanged(plan); var object=object(true,plan);
        long grant=jdbc.queryForObject("SELECT revision FROM plan_review_grant WHERE project_id=?",Long.class,project);
        if(now()>=number(preview,"expires_at"))throw fail("PREVIEW_EXPIRED",410,"预览已过期，请重新预览");
        if(!state(object).editable()||source.version()!=number(preview,"source_version")||grant!=number(preview,"grant_revision")||number(object,"update_time")!=number(preview,"plan_revision"))throw fail("PREVIEW_STALE",409,"计划或授权已变化，请重新预览");
        var ids="added".equals(kind)?source.present().stream().sorted().toList():List.<String>of();
        int from=(int)Math.min((page-1L)*size,ids.size()); var items=new ArrayList<Map<String,Object>>();
        for(String id:ids.subList(from,Math.min(from+size,ids.size()))){
            var item=new LinkedHashMap<>(jdbc.queryForMap("SELECT id,num,name FROM functional_case WHERE id=? AND project_id=?",id,project));
            item.put("reentered",false); item.put("conclusion","UN_REVIEWED"); item.put("historyReadOnly",false); items.add(item);
        }
        return Map.of("items",items,"total",ids.size(),"page",page,"size",size);
    }

    public Map<String, Object> history(String review, String caseId, Long epoch, int page, int size, String actor) {
        page(page, size);
        var object = object(false, review);
        String project = text(object, "project_id");
        authorization.requireRead(actor, project, false);
        String where = "review_id=? AND project_id=?";
        var args = new ArrayList<Object>(List.of(review, project));
        if (caseId != null && !caseId.isBlank()) { where += " AND case_id=?"; args.add(caseId); }
        if (epoch != null) { where += " AND epoch=?"; args.add(epoch); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_member_history WHERE " + where, Long.class, args.toArray());
        args.add(size); args.add((page - 1L) * size);
        var items = new ArrayList<Map<String, Object>>();
        for (var row : jdbc.queryForList("SELECT case_id,epoch,source_epoch,exited_at,exit_reason,case_snapshot,review_snapshot,history_snapshot FROM plan_review_member_history WHERE " + where + " ORDER BY exited_at DESC,case_id,epoch DESC LIMIT ? OFFSET ?", args.toArray())) {
            var item = new LinkedHashMap<String, Object>();
            item.put("caseId", row.get("case_id")); item.put("epoch", row.get("epoch"));
            item.put("exitedAt", row.get("exited_at")); item.put("reason", row.get("exit_reason"));
            for (String field : List.of("case_snapshot", "review_snapshot", "history_snapshot")) {
                Object value = row.get(field);
                if (value instanceof java.sql.Blob blob) {
                    try { value = blob.getBytes(1, Math.toIntExact(blob.length())); }
                    catch (java.sql.SQLException error) { throw fail("HISTORY_READ_FAILED", 503, "历史快照暂不可读取"); }
                }
                if (value instanceof byte[] bytes) item.put(field, JSON.parseObject(new String(bytes, StandardCharsets.UTF_8)));
            }
            items.add(item);
        }
        return Map.of("items", items, "total", total == null ? 0 : total, "page", page, "size", size, "readonly", true);
    }

    /** Called inside an already-authorized original deletion transaction, before deleting either object. */
    public void deletingObject(boolean planSide, String id, String project) {
        var object = object(planSide, id);
        if (!project.equals(object.get("project_id"))) throw fail("RESOURCE_NOT_ACCESSIBLE", 404, "对象不可访问");
        if (planSide) persistence.lockSource(id);
        else persistence.lockReview(id);
        var rows = jdbc.queryForList("SELECT * FROM plan_review_relation WHERE project_id=? AND "
                + (planSide ? "active_plan_id" : "active_review_id") + "=? FOR UPDATE", project, id);
        for (var row : rows) {
            String reason = planSide ? "SOURCE_DELETED" : "TARGET_DELETED";
            jdbc.update("UPDATE plan_review_relation SET state='ENDED',sync_state='ENDED',active_plan_id=NULL,active_review_id=NULL,ended_reason=?,update_time=? WHERE id=? AND generation=? AND state='ACTIVE'",
                    reason, now(), row.get("id"), row.get("generation"));
            jdbc.update("UPDATE plan_review_operation SET state='TERMINATED',stage='TERMINATED',safe_error=?,revision=revision+1,update_time=? WHERE relation_id=? AND generation=? AND state<>'TERMINATED'",
                    reason, now(), row.get("id"), row.get("generation"));
            audit(project, text(row, "id"), text(row, "generation"), null, reason, "plan-review-lifecycle", number(row, "source_version"), 0, 0, "ENDED", reason);
        }
        if (!planSide) {
            jdbc.update("DELETE FROM plan_review_target_member WHERE review_id=?", id);
            jdbc.update("DELETE FROM plan_review_member_history WHERE review_id=? AND project_id=?", id, project);
        }
    }

    /** Internal creation journal only: an empty newly reserved review needs no destructive preview. */
    public void linkCreatedPair(String operationId, String actor) {
        var rows = jdbc.queryForList("SELECT * FROM plan_review_operation WHERE id=?", operationId);
        if (rows.size() != 1) throw fail("RESOURCE_NOT_ACCESSIBLE", 404, "创建操作不可访问");
        var initial = rows.getFirst();
        String project = text(initial, "project_id"), plan = text(initial, "plan_id"), review = text(initial, "review_id");
        if (!actor.equals(initial.get("executing_actor")) || !Set.of("CREATE_PAIR", "COPY_PAIR", "CREATE_REVIEW").contains(initial.get("operation_type"))) {
            throw fail("RESOURCE_NOT_ACCESSIBLE", 404, "创建操作不可访问");
        }
        var context = lockPair(plan, review);
        var operation = jdbc.queryForMap("SELECT * FROM plan_review_operation WHERE id=? FOR UPDATE", operationId);
        if (!actor.equals(operation.get("executing_actor")) || "TERMINATED".equals(operation.get("state"))) {
            throw fail("OPERATION_TERMINATED", 409, "创建操作已终止");
        }
        var source = persistence.sourceChanged(plan);
        boolean reviewOnly="CREATE_REVIEW".equals(operation.get("operation_type"));
        if (reviewOnly ? !PlanReviewPolicy.allowed(PlanReviewPolicy.Action.CREATE_LINKED_REVIEW,authorization.current(actor,project,true))
                : !PlanReviewPolicy.allowedCreatePair(authorization.current(actor, project, true), Math.toIntExact(number(operation,"initial_member_count")))) {
            throw fail("PERMISSION_REQUIRED", 403, "缺少创建关联计划的当前权限或服务授权");
        }
        var existing = occupied(plan, review, project);
        if (operation.get("relation_id") != null) {
            if (existing.size() != 1 || !Objects.equals(operation.get("relation_id"), existing.getFirst().get("id"))
                    || !Objects.equals(operation.get("generation"), existing.getFirst().get("generation"))) {
                throw fail("RELATION_CHANGED", 409, "创建操作的原关系已结束，不会自动重新关联");
            }
            return;
        }
        requireEditable(context.plan, context.review);
        if (!existing.isEmpty() || !targetIds(review).isEmpty()) {
            throw fail("RESERVATION_CONFLICT", 409, "预留对象已有关系或评审成员，请查询原操作");
        }
        String id = uuid(), generation = reviewOnly ? text(operation,"generation") : uuid();
        jdbc.update("""
                INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,active_plan_id,
                active_review_id,state,sync_state,source_version,source_changed_at,original_actor,create_time,update_time)
                VALUES(?,?,?,?,?,?,?,'ACTIVE','PENDING',?,?,?,?,?)
                """, id, project, plan, review, generation, plan, review, source.version(), now(), actor, now(), now());
        jdbc.update("UPDATE plan_review_operation SET relation_id=?,generation=?,stage='TARGET_PENDING',state='PENDING',revision=revision+1,update_time=? WHERE id=?",
                id, generation, now(), operationId);
        audit(project, id, generation, operationId, "CREATE_PAIR", actor, source.version(), 0, 0, "PENDING", null);
    }

    public Map<String, Object> unlink(String relationId, String generation, String requestKey, String actor) {
        key(requestKey);
        var initial = relation(relationId);
        String project = text(initial, "project_id");
        authorization.require(PlanReviewPolicy.Action.UNLINK, actor, project);
        var context = lockPair(text(initial, "plan_id"), text(initial, "review_id"));
        authorization.require(PlanReviewPolicy.Action.UNLINK, actor, project);
        var current = currentRelation(relationId);
        if (!generation.equals(current.get("generation"))) throw fail("RELATION_CHANGED", 409, "关系代次已变化，请刷新");
        if (!"ACTIVE".equals(current.get("state"))) return Map.of("relationStatus", "ENDED", "generation", generation);
        requireEditable(context.plan, context.review);
        jdbc.update("UPDATE plan_review_relation SET state='ENDED',sync_state='ENDED',active_plan_id=NULL,active_review_id=NULL,ended_reason='UNLINKED',approval_pending=0,next_retry_at=NULL,update_time=? WHERE id=? AND generation=?", now(), relationId, generation);
        jdbc.update("UPDATE plan_review_operation SET state='TERMINATED',safe_error='关联已解除',revision=revision+1,update_time=? WHERE relation_id=? AND generation=? AND state<>'COMPLETED'", now(), relationId, generation);
        audit(project, relationId, generation, null, "UNLINK", actor, number(current, "source_version"), 0, 0, "ENDED", "UNLINKED");
        return Map.of("relationStatus", "ENDED", "generation", generation);
    }

    public Map<String, Object> grant(String project, Long expectedRevision, Boolean enabled, String actor) {
        var access = authorization.current(actor, project, enabled != null);
        if (!access.permissions().contains(PlanReviewPolicy.Permission.GRANT_MANAGE)) throw fail("PERMISSION_REQUIRED", 403, "缺少项目服务授权管理权限");
        jdbc.update("INSERT IGNORE INTO plan_review_grant(project_id,update_time) VALUES(?,?)", project, now());
        var current = jdbc.queryForMap("SELECT * FROM plan_review_grant WHERE project_id=? FOR UPDATE", project);
        if (enabled != null) {
            if (expectedRevision == null || expectedRevision != number(current, "revision")) throw fail("GRANT_STALE", 409, "授权已变化，请刷新");
            jdbc.update("UPDATE plan_review_grant SET enabled=?,revision=revision+1,approved_by=?,update_time=? WHERE project_id=?", enabled, actor, now(), project);
            jdbc.update("UPDATE plan_review_relation SET sync_state='RECONFIRM_REQUIRED',next_retry_at=NULL,update_time=? WHERE project_id=? AND state='ACTIVE'", now(), project);
            audit(project, null, null, null, enabled ? "GRANT_RESTORED" : "GRANT_REVOKED", actor, 0, 0, 0, "SUCCEEDED", null);
            current = jdbc.queryForMap("SELECT * FROM plan_review_grant WHERE project_id=?", project);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("projectId", current.get("project_id")); result.put("revision", current.get("revision"));
        result.put("enabled", truth(current.get("enabled"))); result.put("updatedAt", current.get("update_time"));
        if (current.get("approved_by") != null) result.put("approvedBy", current.get("approved_by"));
        return result;
    }

    public Map<String, Object> operations(String project, String id, String actor, int page, int size) {
        return operations(project,id,actor,page,size,false);
    }
    public Map<String,Object> operations(String project,String id,String actor,int page,int size,boolean allProject){return operations(project,id,actor,page,size,allProject,null,null,null);}
    public Map<String,Object> operations(String project,String id,String actor,int page,int size,boolean allProject,String sourceKind,String operationType,String requestKey){
        return operations(project,id,actor,page,size,allProject,sourceKind,operationType,requestKey,null,null);
    }
    public Map<String,Object> operations(String project,String id,String actor,int page,int size,boolean allProject,String sourceKind,String operationType,String requestKey,String relationId,String generation){
        page(page, size);
        if (relationId != null) {
            authorization.requireRead(actor, project, true); authorization.requireRead(actor, project, false);
            if (generation == null || jdbc.queryForList("SELECT id FROM plan_review_relation WHERE id=? AND project_id=? AND generation=? AND state='ACTIVE'",String.class,relationId,project,generation).size()!=1)
                throw fail("RELATION_CHANGED",409,"管理上下文已失效，请重新选择");
        }
        var currentAccess=authorization.current(actor, project, false);
        if(allProject && !currentAccess.hasAll(Set.of(PlanReviewPolicy.Permission.TAKEOVER_APPROVE,PlanReviewPolicy.Permission.PLAN_READ)))throw fail("PERMISSION_REQUIRED",403,"Project takeover approval permission required");
        boolean relationScope = relationId != null;
        String where = " project_id=?"+(allProject||relationScope?"":" AND (executing_actor=? OR original_actor=?)") + (id == null ? "" : " AND id=?");
        List<Object> parameters = new ArrayList<>();parameters.add(project);if(!allProject&&!relationScope){parameters.add(actor);parameters.add(actor);} if (id != null) parameters.add(id);
        if(allProject&&!currentAccess.permissions().contains(PlanReviewPolicy.Permission.REVIEW_READ))where+=" AND operation_type='COPY_GROUP'";
        if(sourceKind!=null){if(sourceKind.length()>32)throw fail("INVALID_FIELD",400,"Invalid source kind");where+=" AND source_kind=?";parameters.add(sourceKind);}
        if(operationType!=null){if(operationType.length()>32)throw fail("INVALID_FIELD",400,"Invalid operation type");where+=" AND operation_type=?";parameters.add(operationType);}
        if(requestKey!=null){key(requestKey);where+=" AND request_key=?";parameters.add(requestKey);}
        if(relationId!=null){where+=" AND relation_id=? AND generation=?";parameters.add(relationId);parameters.add(generation);}
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_operation WHERE " + where, Long.class, parameters.toArray());
        parameters.add(size); parameters.add((page - 1L) * size);
        var rows = jdbc.queryForList("SELECT * FROM plan_review_operation WHERE " + where + " ORDER BY create_time DESC,id LIMIT ? OFFSET ?", parameters.toArray());
        var items = new ArrayList<Map<String, Object>>();
        var access = authorization.current(actor, project, false);
        boolean bothRead = access.permissions().containsAll(Set.of(PlanReviewPolicy.Permission.PLAN_READ, PlanReviewPolicy.Permission.REVIEW_READ));
        for (var row : rows) {
            if (bothRead||("COPY_GROUP".equals(row.get("operation_type"))&&access.permissions().contains(PlanReviewPolicy.Permission.PLAN_READ))) {
                var item=operationResult(row);
                boolean groupOnly="COPY_GROUP".equals(row.get("operation_type"));
                if(groupOnly){item.remove("reviewId");item.remove("relationId");item.remove("generation");}
                boolean owner=actor.equals(row.get("executing_actor")),open=!Set.of("COMPLETED","SKIPPED","TERMINATED").contains(row.get("state"));
                boolean creation=Set.of("CREATE_PAIR","COPY_PAIR").contains(row.get("operation_type"));
                boolean imported="IMPORT_FOLLOWUP".equals(row.get("source_kind")),append="APPEND".equals(row.get("operation_type"));
                boolean linkedReview="CREATE_REVIEW".equals(row.get("operation_type"))&&Set.of("WEB","AGENT").contains(row.get("source_kind"));
                boolean permitted=(creation&&PlanReviewPolicy.allowedCreatePair(access,Math.toIntExact(number(row,"initial_member_count"))));
                if(linkedReview)permitted=PlanReviewPolicy.allowed(PlanReviewPolicy.Action.CREATE_LINKED_REVIEW,access);
                if(groupOnly)permitted=access.hasAll(Set.of(PlanReviewPolicy.Permission.PLAN_ADD,PlanReviewPolicy.Permission.PLAN_READ));
                if(imported&&append)permitted=PlanReviewPolicy.allowed(PlanReviewPolicy.Action.APPEND,access);
                if(imported)permitted=permitted&&access.permissions().contains(PlanReviewPolicy.Permission.IMPORT);
                boolean supported=linkedReview||((creation||groupOnly)&&"PLAN_SERVICE".equals(row.get("source_kind")))||(creation&&"HUB_IMPORT".equals(row.get("source_kind")))||(imported&&(creation||append));
                boolean child=((String)row.get("request_key")).startsWith("import:");
                item.put("canResume",owner&&open&&permitted&&supported&&row.get("executing_credential_id")==null&&!child);
                item.put("canTerminate",owner&&open&&permitted&&supported&&row.get("executing_credential_id")==null&&!child);
                item.put("canRequestTakeover",open&&supported&&!child&&(owner||allProject));
                items.add(item);
            }
            else {
                var safe = selected(row, "stage", "state", "revision"); safe.put("operationId",row.get("id")); items.add(safe);
            }
        }
        return Map.of("items", items, "total", count == null ? 0 : count, "page", page, "size", size);
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 30000)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void drain() {
        // Commit each bounded batch separately, without imposing another one-second delay per 100 members.
        // The time/batch bound leaves scheduler capacity for other jobs; every commit rechecks the fences.
        long deadline=System.nanoTime()+java.time.Duration.ofSeconds(8).toNanos();
        for(int batch=0;batch<32&&System.nanoTime()<deadline;batch++)if(!drainBatch())break;
    }
    private boolean drainBatch(){
        var due = jdbc.queryForList("""
                SELECT id,generation,attempts,retry_round,applied_batches FROM plan_review_relation WHERE state='ACTIVE' AND protocol_version=2
                AND sync_state IN ('PENDING','WAITING_RETRY','DELAYED') AND (next_retry_at IS NULL OR next_retry_at<=?)
                ORDER BY update_time,id LIMIT 1
                """, now());
        if (due.isEmpty()) return false;
        String id = text(due.getFirst(), "id"), generation = text(due.getFirst(), "generation");
        try {
            tx.executeWithoutResult(status -> synchronize(id, generation));
            var completed=jdbc.queryForList("SELECT applied_batches FROM plan_review_relation WHERE id=? AND generation=?",Long.class,id,generation);
            return completed.size()==1&&completed.getFirst()>number(due.getFirst(),"applied_batches");
        }
        catch (Exception failure) {
            tx.executeWithoutResult(status -> {
                var rows = jdbc.queryForList("SELECT * FROM plan_review_relation WHERE id=? AND generation=? AND state='ACTIVE' FOR UPDATE", id, generation);
                if (rows.isEmpty()) return;
                var row = rows.getFirst();
                if (Set.of("RECONFIRM_REQUIRED", "ENDED", "FAILED_MANUAL","SYNCED").contains(text(row, "sync_state"))
                        ||number(row,"retry_round")!=number(due.getFirst(),"retry_round")
                        ||number(row,"attempts")!=number(due.getFirst(),"attempts")
                        ||number(row,"applied_batches")!=number(due.getFirst(),"applied_batches")) return;
                int completed = (int) number(row, "attempts") + 1;
                boolean exhausted = completed >= 4;
                Long next = exhausted ? null : now() + new long[]{10_000, 30_000, 60_000}[completed - 1];
                jdbc.update("UPDATE plan_review_relation SET attempts=?,next_retry_at=?,sync_state=?,safe_error=?,update_time=? WHERE id=? AND generation=?",
                        completed, next, exhausted ? "FAILED_MANUAL" : "WAITING_RETRY", "成员同步未完成，请查看操作记录；源提交已保留", now(), id, generation);
                audit(text(row, "project_id"), id, generation, null, "SYNC_FAILED", "plan-review-sync", number(row, "source_version"), 0, 0,
                        exhausted ? "FAILED_MANUAL" : "WAITING_RETRY", "TARGET_WRITE_FAILED");
            });
            return false;
        }
    }

    /** The scheduler invokes inside an explicit bounded transaction, not through self-invoked AOP. */
    void synchronize(String id, String generation) {
        var initial = relation(id);
        if (!generation.equals(initial.get("generation")) || !"ACTIVE".equals(initial.get("state"))) return;
        var context = lockPair(text(initial, "plan_id"), text(initial, "review_id"));
        var source = persistence.sourceChanged(text(initial, "plan_id"));
        var current = currentRelation(id);
        if (!generation.equals(current.get("generation")) || !"ACTIVE".equals(current.get("state"))) return;
        if (!editable(context.plan, context.review) || !truth(context.grant.get("enabled"))) {
            jdbc.update("UPDATE plan_review_relation SET next_retry_at=NULL,update_time=? WHERE id=?", now(), id);
            return;
        }
        if (Set.of("RECONFIRM_REQUIRED", "FAILED_MANUAL").contains(text(current, "sync_state"))) return;
        if("WAITING_RETRY".equals(current.get("sync_state"))&&current.get("next_retry_at")!=null&&number(current,"next_retry_at")>now())return;
        String reviewId = text(current, "review_id");
        if (truth(current.get("approval_pending")) && (source.version() != number(current, "approved_source_version")
                || number(current, "opinion_revision") != number(current, "approved_opinion_revision")
                || number(context.grant, "revision") != number(current, "approved_grant_revision")
                || !fingerprint(reviewId).equals(current.get("approved_target_fingerprint")))) {
            jdbc.update("UPDATE plan_review_relation SET sync_state='RECONFIRM_REQUIRED',next_retry_at=NULL,update_time=? WHERE id=?", now(), id);
            return;
        }
        var difference = difference(reviewId, source, false);
        List<String> exits = new ArrayList<>(difference.removed()); exits.addAll(difference.reentered());
        List<String> adds = new ArrayList<>(difference.added()); adds.addAll(difference.reentered());
        int count = 0, removed = 0, added = 0;
        for (String caseId : exits) if (count++ < 100) { archive(current, caseId, difference.reentered().contains(caseId) ? "REENTERED" : "SOURCE_REMOVED"); removed++; }
        for (String caseId : adds) {
            if (count >= 100) break;
            // A reentered identity is added only after its old epoch has actually exited.
            if (difference.reentered().contains(caseId) && jdbc.queryForObject("SELECT COUNT(*) FROM case_review_functional_case WHERE review_id=? AND case_id=?", Integer.class, reviewId, caseId) != 0) continue;
            add(current, caseId, source.epochs().get(caseId)); count++; added++;
        }
        recount(reviewId);
        boolean converged = difference(reviewId, source, false).empty();
        jdbc.update("""
                UPDATE plan_review_relation SET applied_batches=applied_batches+1,approved_target_fingerprint=?,
                applied_version=CASE WHEN ? THEN ? ELSE applied_version END,approval_pending=CASE WHEN ? THEN 0 ELSE approval_pending END,
                sync_state=?,last_success_at=CASE WHEN ? THEN ? ELSE last_success_at END,attempts=CASE WHEN ? THEN 0 ELSE attempts END,
                next_retry_at=?,safe_error=NULL,update_time=? WHERE id=? AND generation=? AND state='ACTIVE'
                """, fingerprint(reviewId), converged, source.version(), converged, converged ? "SYNCED" : "PENDING",
                converged, now(), converged, converged ? null : now(), now(), id, generation);
        if (converged) jdbc.update("UPDATE plan_review_operation SET stage='COMPLETED',state='COMPLETED',revision=revision+1,update_time=? WHERE relation_id=? AND generation=? AND stage='TARGET_PENDING' AND state='PENDING'", now(), id, generation);
        audit(text(current, "project_id"), id, generation, text(current, "approval_id"), "SYNC_BATCH", "plan-review-sync", source.version(), added, removed,
                converged ? "SYNCED" : "PENDING", null);
    }

    private void initializeTarget(String review, PlanReviewSourceMembership source) {
        for (String caseId : targetIds(review)) jdbc.update("""
                INSERT INTO plan_review_target_member(review_id,case_id,epoch,source_epoch,present) VALUES(?,?,1,?,1)
                ON DUPLICATE KEY UPDATE epoch=epoch+1,source_epoch=VALUES(source_epoch),present=1
                """, review, caseId, source.epochs().getOrDefault(caseId, 1L));
    }
    /** Migration adopts the exact surviving objects; actual member catch-up still requires fresh M08. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Map<String,Object> adoptLegacyPair(String project,String plan,String review,String changeId,String actor){
        var access=authorization.current(actor,project,true);
        if(!access.permissions().contains(PlanReviewPolicy.Permission.LEGACY_MIGRATE))throw fail("PERMISSION_REQUIRED",403,"缺少当前项目迁移执行授权");
        authorization.require(PlanReviewPolicy.Action.PAIR,actor,project);
        var proofs=jdbc.queryForList("SELECT approved_by FROM plan_review_legacy_change WHERE id=? AND project_id=? AND state='APPROVED' AND rehearsal_id IS NOT NULL AND backup_at IS NOT NULL FOR UPDATE",changeId,project);
        if(proofs.size()!=1||proofs.getFirst().get("approved_by")==null)throw fail("MIGRATION_APPROVAL_REQUIRED",403,"原迁移清单尚未完成备份、隔离演练和负责人批准");
        var owner=authorization.current((String)proofs.getFirst().get("approved_by"),project,true);
        if(!owner.permissions().containsAll(Set.of(PlanReviewPolicy.Permission.TAKEOVER_APPROVE,PlanReviewPolicy.Permission.PLAN_READ,PlanReviewPolicy.Permission.REVIEW_READ)))throw fail("MIGRATION_APPROVAL_REQUIRED",403,"原迁移审批人当前资格已失效");
        var context=lockPair(plan,review);
        if(!project.equals(context.plan.get("project_id")))throw fail("RESOURCE_NOT_ACCESSIBLE",404,"迁移对象不属于当前项目");
        requireEditable(context.plan,context.review);persistence.requireNoLegacyOwnership(project,plan,review);
        var occupied=occupied(plan,review,project);
        if(!occupied.isEmpty()){
            if(occupied.size()!=1||!plan.equals(occupied.getFirst().get("plan_id"))||!review.equals(occupied.getFirst().get("review_id")))throw fail("RELATION_OCCUPIED",409,"原两对象存在其他占用，不会自动选择或替换目标");
            var existing=occupied.getFirst();return Map.of("relationId",existing.get("id"),"generation",existing.get("generation"),"created",false);
        }
        var source=persistence.sourceChanged(plan);String id=uuid(),generation=uuid();long now=now();
        jdbc.update("INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,active_plan_id,active_review_id,state,sync_state,source_version,source_changed_at,original_actor,create_time,update_time) VALUES(?,?,?,?,?,?,?,'ACTIVE','RECONFIRM_REQUIRED',?,?,?,?,?)",id,project,plan,review,generation,plan,review,source.version(),now,actor,now,now);
        initializeTarget(review,source);
        audit(project,id,generation,changeId,"LEGACY_MAP",actor,source.version(),0,0,"RECONFIRM_REQUIRED",null);
        return Map.of("relationId",id,"generation",generation,"created",true);
    }

    private void archive(Map<String, Object> relation, String caseId, String reason) {
        String review = text(relation, "review_id");
        var member = jdbc.queryForMap("SELECT * FROM plan_review_target_member WHERE review_id=? AND case_id=? AND present=1 FOR UPDATE", review, caseId);
        var current = jdbc.queryForList("SELECT * FROM case_review_functional_case WHERE review_id=? AND case_id=? FOR UPDATE", review, caseId);
        var caseSnapshot = jdbc.queryForList("SELECT * FROM functional_case WHERE id=? AND project_id=? FOR UPDATE", caseId,relation.get("project_id"));
        var opinions = jdbc.queryForList("SELECT * FROM case_review_history WHERE review_id=? AND case_id=? AND deleted=0 FOR UPDATE", review, caseId);
        jdbc.update("""
                INSERT INTO plan_review_member_history(review_id,case_id,epoch,project_id,relation_id,generation,source_epoch,
                case_snapshot,review_snapshot,history_snapshot,exited_at,exit_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """, review, caseId, number(member, "epoch"), relation.get("project_id"), relation.get("id"), relation.get("generation"),
                number(member, "source_epoch"), bytes(caseSnapshot), bytes(current), bytes(opinions), now(), reason);
        jdbc.update("UPDATE case_review_history SET deleted=1 WHERE review_id=? AND case_id=? AND deleted=0", review, caseId);
        jdbc.update("DELETE FROM case_review_functional_case_user WHERE review_id=? AND case_id=?", review, caseId);
        jdbc.update("DELETE FROM case_review_functional_case WHERE review_id=? AND case_id=?", review, caseId);
        jdbc.update("UPDATE plan_review_target_member SET present=0 WHERE review_id=? AND case_id=?", review, caseId);
    }

    private void add(Map<String, Object> relation, String caseId, long sourceEpoch) {
        String review = text(relation, "review_id");
        long position = Optional.ofNullable(jdbc.queryForObject("SELECT MAX(pos) FROM case_review_functional_case WHERE review_id=?", Long.class, review)).orElse(0L) + 5000;
        jdbc.update("INSERT INTO case_review_functional_case(id,review_id,case_id,status,create_time,create_user,update_time,pos) VALUES(?,?,?,'UN_REVIEWED',?,'plan-review-sync',?,?)", uuid(), review, caseId, now(), now(), position);
        jdbc.update("INSERT INTO case_review_functional_case_user(review_id,case_id,user_id) SELECT review_id,?,user_id FROM case_review_user WHERE review_id=?", caseId, review);
        jdbc.update("""
                INSERT INTO plan_review_target_member(review_id,case_id,epoch,source_epoch,present) VALUES(?,?,1,?,1)
                ON DUPLICATE KEY UPDATE epoch=epoch+1,source_epoch=VALUES(source_epoch),present=1
                """, review, caseId, sourceEpoch);
    }

    private Map<String,Object> statusCounts(List<String> statuses){
        long passed=statuses.stream().filter("PASS"::equals).count(),failed=statuses.stream().filter("UN_PASS"::equals).count();
        return Map.of("total",(long)statuses.size(),"passed",passed,"failed",failed,"pending",statuses.stream().filter("UN_REVIEWED"::equals).count(),"finished",passed+failed);
    }

    private void recount(String review) {
        var counts = statusCounts(jdbc.queryForList("SELECT status FROM case_review_functional_case WHERE review_id=? FOR UPDATE",String.class,review));
        long total = number(counts, "total"), passed = number(counts, "passed");
        java.math.BigDecimal rate = total == 0 ? java.math.BigDecimal.ZERO : java.math.BigDecimal.valueOf(passed * 100).divide(java.math.BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
        jdbc.update("UPDATE case_review SET case_count=?,pass_rate=? WHERE id=?", total, rate, review);
    }

    private PlanReviewReconciliation.Difference difference(String review, PlanReviewSourceMembership source, boolean first) {
        Set<String> targets = targetIds(review);
        Map<String, Long> applied = new HashMap<>();
        if (first) for (String id : targets) applied.put(id, source.epochs().getOrDefault(id, 1L));
        else for (var row : jdbc.queryForList("SELECT case_id,source_epoch FROM plan_review_target_member WHERE review_id=? AND present=1 FOR UPDATE", review)) applied.put(text(row, "case_id"), number(row, "source_epoch"));
        return PlanReviewReconciliation.calculate(source.present(), targets, source.epochs(), applied);
    }

    private Set<String> targetIds(String review) {
        return new TreeSet<>(jdbc.queryForList("SELECT case_id FROM case_review_functional_case WHERE review_id=? ORDER BY case_id FOR UPDATE", String.class, review));
    }

    private String fingerprint(String review) {
        // Existing preview/approval hashes retain their historical format until consumed or expired.
        return new PlanReviewFingerprintReader(jdbc).read(review, PlanReviewFingerprintReader.ReadMode.LOCKED, PlanReviewFingerprintReader.Format.APPLICATION_V1);
    }

    private record Context(Map<String, Object> plan, Map<String, Object> review, Map<String, Object> grant) { }

    private Context lockPair(String planId, String reviewId) {
        var plan = object(true, planId); var review = object(false, reviewId);
        if (!Objects.equals(plan.get("project_id"), review.get("project_id"))) throw fail("RESOURCE_NOT_ACCESSIBLE", 404, "对象不可访问");
        String project = text(plan, "project_id");
        jdbc.update("INSERT IGNORE INTO plan_review_grant(project_id,update_time) VALUES(?,?)", project, now());
        var grant = jdbc.queryForMap("SELECT * FROM plan_review_grant WHERE project_id=? FOR UPDATE", project);
        plan = jdbc.queryForMap("SELECT * FROM test_plan WHERE id=? AND type='TEST_PLAN' FOR UPDATE", planId);
        plan = planStates.resolve(plan);
        review = jdbc.queryForMap("SELECT * FROM case_review WHERE id=? FOR UPDATE", reviewId);
        occupied(planId, reviewId, project);
        return new Context(plan, review, grant);
    }

    private List<Map<String, Object>> occupied(String plan, String review, String project) {
        return jdbc.queryForList("SELECT * FROM plan_review_relation WHERE project_id=? AND (active_plan_id=? OR active_review_id=?) ORDER BY id FOR UPDATE", project, plan, review);
    }

    /** Used only after acquiring the project/object locks; bypasses an older RR snapshot. */
    private Map<String, Object> currentRelation(String id) {
        var rows = jdbc.queryForList("SELECT * FROM plan_review_relation WHERE id=? FOR UPDATE", id);
        if (rows.size() != 1) throw fail("RESOURCE_NOT_ACCESSIBLE", 404, "关系不可访问");
        return rows.getFirst();
    }

    private Map<String, Object> relation(String id) {
        var rows = jdbc.queryForList("SELECT * FROM plan_review_relation WHERE id=?", id);
        if (rows.size() != 1) throw fail("RESOURCE_NOT_ACCESSIBLE", 404, "关系不可访问");
        return rows.getFirst();
    }

    private Map<String, Object> object(boolean plan, String id) {
        if (id == null || id.isBlank() || id.length() > 50) throw fail("INVALID_FIELD", 400, "对象标识无效");
        var rows = jdbc.queryForList("SELECT * FROM " + (plan ? "test_plan" : "case_review") + " WHERE id=?" + (plan ? " AND type='TEST_PLAN'" : ""), id);
        if (rows.size() != 1) throw fail("RESOURCE_NOT_ACCESSIBLE", 404, "对象不可访问");
        return plan ? planStates.resolve(rows.getFirst()) : rows.getFirst();
    }

    private Map<String, Object> syncSummary(Map<String, Object> relation, Map<String, Object> plan, Map<String, Object> review, boolean grant) {
        List<String> blockers = new ArrayList<>();
        if (state(plan) == PlanReviewPolicy.State.UNKNOWN || state(review) == PlanReviewPolicy.State.UNKNOWN) blockers.add("UNKNOWN_STATE");
        if (!state(plan).editable()) blockers.add("PLAN_LOCKED");
        if (!state(review).editable()) blockers.add("REVIEW_LOCKED");
        if (!grant) blockers.add("GRANT_REVOKED");
        if ("RECONFIRM_REQUIRED".equals(relation.get("sync_state"))) blockers.add("RECONFIRM_REQUIRED");
        if ("FAILED_MANUAL".equals(relation.get("sync_state"))) blockers.add("RETRY_EXHAUSTED");
        var result = new LinkedHashMap<String, Object>();
        result.put("state", relation.get("sync_state")); result.put("sourceVersion", relation.get("source_version"));
        result.put("appliedVersion", relation.get("applied_version")); result.put("attempts", relation.get("attempts"));
        if (relation.get("last_success_at") != null) result.put("lastSuccessAt", relation.get("last_success_at"));
        if (relation.get("next_retry_at") != null) result.put("nextRetryAt", relation.get("next_retry_at"));
        if (relation.get("safe_error") != null) result.put("safeError", relation.get("safe_error"));
        if (!"SYNCED".equals(relation.get("sync_state")) && now() - number(relation, "source_changed_at") > 30_000) result.put("delayed", true);
        result.put("editable", editable(plan, review) && grant);
        result.put("blockers", blockers); return result;
    }

    private void audit(String project, String relation, String generation, String operation, String action, String actor, long version, int added, int removed, String result, String reason) {
        jdbc.update("INSERT INTO plan_review_audit(id,project_id,relation_id,generation,operation_id,action,actor,service_actor,source_version,added_count,removed_count,result,safe_reason,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                uuid(), project, relation, generation, operation, action, actor, actor.equals("plan-review-sync") ? actor : null, version, added, removed, result, reason, now());
    }

    private static Map<String, Object> operationResult(Map<String, Object> row) {
        var result = selected(row, "generation", "stage", "state", "revision");
        if(row.get("request_key")!=null)result.put("requestKey",row.get("request_key"));
        if(row.get("source_kind")!=null)result.put("sourceKind",row.get("source_kind"));
        result.put("operationId", row.get("id"));
        if (row.get("relation_id") != null) result.put("relationId", row.get("relation_id"));
        if (row.get("plan_id") != null) result.put("planId", row.get("plan_id"));
        if (row.get("review_id") != null) result.put("reviewId", row.get("review_id"));
        if (row.get("operation_type") != null) result.put("operationType", row.get("operation_type"));
        if (row.get("safe_error") != null) result.put("safeError", row.get("safe_error"));
        return result;
    }
    private static Map<String, Object> selected(Map<String, Object> row, String... keys) {
        var result = new LinkedHashMap<String, Object>(); for (String key : keys) if (row.get(key) != null) result.put(key, row.get(key)); return result;
    }
    private static boolean side(String side) { if (side == null || !Set.of("plan", "review").contains(side)) throw fail("INVALID_SIDE", 400, "对象类型无效"); return side.equals("plan"); }
    private static boolean editable(Map<String, Object> plan, Map<String, Object> review) { return state(plan).editable() && state(review).editable(); }
    private static void requireEditable(Map<String, Object> plan, Map<String, Object> review) { if (!editable(plan, review)) throw fail("OBJECT_LOCKED", 409, "双方必须处于待开始或进行中状态"); }
    private static PlanReviewPolicy.State state(Map<String, Object> row) { return PlanReviewPolicy.State.parse(text(row, "status")); }
    private static void page(int page, int size) { if (page < 1 || size < 1 || size > 100) throw fail("INVALID_PAGE", 400, "分页参数无效"); }
    private static void key(String key) { if (key == null || key.isBlank() || key.length() > 128) throw fail("REQUEST_KEY_REQUIRED", 400, "请提供有效请求标识"); }
    private static long number(Map<String, Object> row, String key) { return row.get(key) instanceof Number n ? n.longValue() : 0; }
    private static String text(Map<String, Object> row, String key) { return Objects.toString(row.get(key), ""); }
    private static boolean truth(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() != 0; }
    private static long now() { return System.currentTimeMillis(); }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static byte[] bytes(Object value) { return JSON.toJSONString(value).getBytes(StandardCharsets.UTF_8); }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private static PlanReviewFailure fail(String code, int status, String message) { return new PlanReviewFailure(code, status, message); }
}
