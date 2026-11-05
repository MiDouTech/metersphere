package io.metersphere.plan.service;

import io.metersphere.functional.planreview.*;
import io.metersphere.functional.request.BaseAssociateCaseRequest;
import io.metersphere.functional.request.CaseReviewRequest;
import io.metersphere.functional.service.CaseReviewService;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Reserve IDs independently; plan+review+relation creation itself commits atomically. */
@Service
public class PlanReviewCreationJournal {
    @Resource private JdbcTemplate jdbc;
    @Resource private PlanReviewAuthorization authorization;
    @Resource private PlanReviewApplicationService relations;
    @Resource @Lazy private CaseReviewService reviews;
    @Resource private PlanReviewPlanStateResolver planStates;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private List<PlanReviewExecutionCredential> credentials=List.of();
    private String credentialReference(){
        var references=credentials.stream().map(PlanReviewExecutionCredential::currentReference).filter(Objects::nonNull).distinct().toList();
        if(references.size()>1)throw new PlanReviewFailure("CREDENTIAL_CONTEXT_CONFLICT",403,"执行凭据上下文不明确");
        return references.isEmpty()?null:references.getFirst();
    }

    private void validateCredential(String project,String actor){
        String reference=credentialReference();
        if(reference!=null&&credentials.stream().noneMatch(provider->provider.validate(reference,project,actor)))throw new PlanReviewFailure("CREDENTIAL_CONTEXT_REQUIRED",403,"当前执行凭据已失效或权限不足");
    }

    private void importParent(String project,String actor,String key){
        if(!key.startsWith("import:"))return;
        var rows=jdbc.queryForList("SELECT p.* FROM plan_review_operation p JOIN plan_review_import_followup f ON f.operation_id=p.id WHERE f.job_id=? AND f.project_id=? AND p.project_id=? AND p.source_kind='IMPORT_FOLLOWUP' FOR UPDATE",key.substring(7),project,project);
        if(rows.size()!=1)throw new PlanReviewFailure("PARENT_OPERATION_REQUIRED",409,"Import creation requires its original parent operation");
        var parent=rows.getFirst();
        var access=authorization.current(actor,project,true);
        if(!access.permissions().contains(PlanReviewPolicy.Permission.IMPORT)||!PlanReviewPolicy.allowedCreatePair(access,((Number)parent.get("initial_member_count")).intValue()))throw new PlanReviewFailure("PERMISSION_REQUIRED",403,"导入创建预留需要当前导入和创建关联计划的完整权限");
        String reference=credentialReference();
        if(reference!=null&&credentials.stream().noneMatch(provider->provider.validateImport(reference,project,actor)))throw new PlanReviewFailure("CREDENTIAL_CONTEXT_REQUIRED",403,"原Agent凭据不再具有当前导入权限");
        if(!actor.equals(parent.get("executing_actor"))||!Objects.equals(credentialReference(),parent.get("executing_credential_id"))||Set.of("TERMINATED","COMPLETED").contains(parent.get("state")))throw new PlanReviewFailure("PARENT_OPERATION_CHANGED",409,"Import execution ownership changed");
    }

    public record Reservation(String id, String planId, String reviewId, String state) { }
    private static final ThreadLocal<CopyContext> COPYING = new ThreadLocal<>();
    public record CopyContext(Map<String, Reservation> reservations, PlanReviewSettings settings) implements AutoCloseable {
        @Override public void close() { COPYING.remove(); }
    }
    public CopyContext copyContext(Map<String, Reservation> reservations, PlanReviewSettings settings) {
        if (COPYING.get() != null) throw new IllegalStateException("Nested copy context");
        var context = new CopyContext(Map.copyOf(reservations), settings);
        COPYING.set(context);
        return context;
    }
    public Reservation copyReservation(String source) {
        var context = COPYING.get();
        if (context == null || !context.reservations().containsKey(source)) {
            throw new PlanReviewFailure("REVIEW_SETTINGS_REQUIRED", 400, "复制前须确认关联评审设置");
        }
        return context.reservations().get(source);
    }
    public PlanReviewSettings copySettings() { return Objects.requireNonNull(COPYING.get()).settings(); }
    public int effectiveMembers(String plan, String project) {
        Long count = jdbc.queryForObject("SELECT COUNT(DISTINCT m.functional_case_id) FROM test_plan_functional_case m JOIN functional_case c ON BINARY c.id=BINARY m.functional_case_id AND c.deleted=0 WHERE m.test_plan_id=? AND c.project_id=?",Long.class,plan,project);
        return count == null ? 0 : Math.toIntExact(count);
    }

    private void validateCreation(String project, String actor, String type, PlanReviewSettings settings, int members) {
        if ("COPY_GROUP".equals(type)) {
            var permissions = authorization.current(actor, project, true).permissions();
            if (!permissions.containsAll(Set.of(PlanReviewPolicy.Permission.PLAN_ADD, PlanReviewPolicy.Permission.PLAN_READ))) {
                throw new PlanReviewFailure("PERMISSION_REQUIRED", 403, "缺少复制计划组的当前权限");
            }
        } else validate(project, actor, settings, members);
    }

    /** Hold the project lock for the whole creation transaction, including idempotent replays. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockAndExists(Reservation reservation, String project, String actor) {
        jdbc.queryForMap("SELECT * FROM plan_review_grant WHERE project_id=? FOR UPDATE", project);
        var operation = jdbc.queryForMap("SELECT * FROM plan_review_operation WHERE id=? AND project_id=? FOR UPDATE", reservation.id(), project);
        if (!actor.equals(operation.get("executing_actor")) || !Objects.equals(credentialReference(),operation.get("executing_credential_id")) || "TERMINATED".equals(operation.get("state"))) {
            throw new PlanReviewFailure("OPERATION_TERMINATED", 409, "原操作不可继续");
        }
        validateCredential(project,actor);
        var plans = jdbc.queryForList("SELECT project_id,type FROM test_plan WHERE id=? FOR UPDATE", reservation.planId());
        if (plans.isEmpty()) {
            if(!"VALIDATED".equals(operation.get("stage")))throw new PlanReviewFailure("RESERVED_OBJECT_MISSING",409,"原成功对象已不存在，不会重建已删除对象");
            boolean group="COPY_GROUP".equals(operation.get("operation_type"));
            if(group)validateCreation(project,actor,"COPY_GROUP",null,0);
            else{
                var payload=JSON.parseObject((String)operation.get("request_json"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                var settings=JSON.parseObject(JSON.toJSONString(payload.get("reviewSettings")),PlanReviewSettings.class);
                validate(project,actor,settings,((Number)operation.get("initial_member_count")).intValue());
            }
            return false;
        }
        boolean group = "COPY_GROUP".equals(operation.get("operation_type"));
        if (!project.equals(plans.getFirst().get("project_id")) || !(group?"GROUP":"TEST_PLAN").equals(plans.getFirst().get("type")) || (!group && operation.get("relation_id") == null)) {
            throw new PlanReviewFailure("RESERVATION_CONFLICT", 409, "预留对象状态不一致，请查询原操作");
        }
        if (!group) relations.linkCreatedPair(reservation.id(), actor);
        return true;
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public Reservation existingReservation(String project,String actor,String key,String payload){
        authorization.current(actor,project,true);
        var rows=jdbc.queryForList("SELECT * FROM plan_review_operation WHERE project_id=? AND source_kind='PLAN_SERVICE' AND operation_type='CREATE_PAIR' AND request_key=? FOR UPDATE",project,key);
        if(rows.size()!=1)throw new PlanReviewFailure("RESERVATION_REQUIRED",409,"请在进入业务事务前完成创建预留");
        var operation=rows.getFirst();
        if(!actor.equals(operation.get("executing_actor")) || !Objects.equals(credentialReference(),operation.get("executing_credential_id")) || !hash(payload).equals(operation.get("payload_hash")) || "TERMINATED".equals(operation.get("state")))throw new PlanReviewFailure("RESERVATION_CONFLICT",409,"原创建预留已变化");
        validateCredential(project,actor);importParent(project,actor,key);
        return reservation(operation);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public io.metersphere.plan.domain.TestPlan readReservedPlan(String id) {
        // A locking read sees the latest committed replay result even under REPEATABLE READ.
        var mapper = new org.springframework.jdbc.core.BeanPropertyRowMapper<>(io.metersphere.plan.domain.TestPlan.class) {
            @Override
            protected Object getColumnValue(java.sql.ResultSet rs, int index, java.beans.PropertyDescriptor property) throws java.sql.SQLException {
                if ("tags".equals(property.getName())) {
                    String value = rs.getString(index);
                    return value == null || value.isBlank() ? null : JSON.parseArray(value, String.class);
                }
                return super.getColumnValue(rs, index, property);
            }
        };
        return jdbc.queryForObject("SELECT * FROM test_plan WHERE id=? FOR UPDATE", mapper, id);
    }

    public void validate(String project, String actor, PlanReviewSettings settings, int initialMembers) {
        if (settings == null) throw new PlanReviewFailure("REVIEW_SETTINGS_REQUIRED", 400, "缺少关联评审设置");
        if (!PlanReviewPolicy.allowedCreatePair(authorization.current(actor, project, true), initialMembers)) {
            throw new PlanReviewFailure("PERMISSION_REQUIRED", 403, "缺少新建计划及关联评审的权限，或项目同步服务尚未授权");
        }
        if (!"root".equals(settings.moduleId())) {
            int legal = jdbc.queryForList("SELECT id FROM case_review_module WHERE id=? AND project_id=? FOR UPDATE",String.class,settings.moduleId(),project).size();
            if (legal != 1) throw new PlanReviewFailure("REVIEW_SETTINGS_INVALID", 400, "评审目录已失效，请重新选择");
        }
        for (String reviewer : settings.reviewers()) {
            if (!authorization.current(reviewer, project, true).permissions().contains(PlanReviewPolicy.Permission.REVIEW_OPINION)) {
                throw new PlanReviewFailure("REVIEW_SETTINGS_INVALID", 400, "所选评审人已失效或不具备评审权限");
            }
        }
    }

    /** Current project/group/module ownership, shared by original creation and reserved recovery. */
    @Transactional(propagation=Propagation.MANDATORY)
    public String requirePlanDestination(String project,String group,String module,String type,String actor){return requirePlanDestination(project,group,module,type,actor,true);}
    @Transactional(propagation=Propagation.MANDATORY)
    public String requirePlanDestination(String project,String group,String module,String type,String actor,boolean creating){
        if(project==null||group==null||module==null||type==null||!Set.of("TEST_PLAN","GROUP").contains(type))throw new PlanReviewFailure("INVALID_PLAN_DESTINATION",400,"计划目标设置不完整");
        var access=authorization.current(actor,project,true);
        if("GROUP".equals(type)?!access.hasAll(Set.of(PlanReviewPolicy.Permission.PLAN_READ,PlanReviewPolicy.Permission.PLAN_ADD)):!PlanReviewPolicy.allowedCreatePair(access,0))throw new PlanReviewFailure("PERMISSION_REQUIRED",403,"缺少当前计划创建权限或服务授权");
        jdbc.update("INSERT IGNORE INTO plan_review_grant(project_id,update_time) VALUES(?,?)",project,System.currentTimeMillis());
        jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE",project);
        requirePlanModule(project,module);
        if(io.metersphere.sdk.constants.TestPlanConstants.TEST_PLAN_DEFAULT_GROUP_ID.equals(group))return module;
        if("GROUP".equals(type))throw new PlanReviewFailure("INVALID_PLAN_DESTINATION",400,"计划组不能嵌套到其他计划组");
        var rows=jdbc.queryForList("SELECT id,project_id,module_id,type,status FROM test_plan WHERE id=? AND project_id=? FOR UPDATE",group,project);
        if(rows.size()!=1||!"GROUP".equals(rows.getFirst().get("type"))||!Set.of("NOT_ARCHIVED","PREPARED","UNDERWAY","COMPLETED").contains(Objects.toString(rows.getFirst().get("status"),"")))throw new PlanReviewFailure("TARGET_GROUP_UNAVAILABLE",409,"原目标组不属于当前项目、已删除或已锁定，不会改投其他目标");
        String currentModule=(String)rows.getFirst().get("module_id");requirePlanModule(project,currentModule);
        int count=jdbc.queryForList("SELECT id FROM test_plan WHERE group_id=? AND project_id=? ORDER BY id FOR UPDATE",String.class,group,project).size();
        if(creating&&count>=20)throw new PlanReviewFailure("TARGET_GROUP_FULL",409,"原目标组已达到子计划数量上限");
        return currentModule;
    }
    private void requirePlanModule(String project,String module){
        if(!"root".equals(module)&&(module==null||jdbc.queryForList("SELECT id FROM test_plan_module WHERE id=? AND project_id=? FOR UPDATE",String.class,module,project).size()!=1))throw new PlanReviewFailure("DIRECTORY_UNAVAILABLE",409,"原目标目录不属于当前项目或已失效，不会使用其他目录");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireOverwrite(String project,String target,String actor){
        if(!authorization.current(actor,project,true).permissions().contains(PlanReviewPolicy.Permission.PLAN_UPDATE))throw new PlanReviewFailure("PERMISSION_REQUIRED",403,"覆盖现有计划及文档需要当前计划更新权限");
        if(jdbc.queryForList("SELECT id FROM test_plan WHERE id=? AND project_id=? AND type='TEST_PLAN' FOR UPDATE",String.class,target,project).size()!=1)throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"原覆盖目标不可访问");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Reservation prepare(String project, String actor, String sourceKind, String type, String key,
                               String payload, PlanReviewSettings settings, int initialMembers) {
        validateCreation(project, actor, type, settings, initialMembers);
        if (key == null || key.isBlank() || key.length() > 128) throw new PlanReviewFailure("REQUEST_KEY_REQUIRED", 400, "缺少有效请求标识，请刷新后重试");
        // Project lock serializes same-key reservation and grant revocation without holding user data.
        jdbc.update("INSERT IGNORE INTO plan_review_grant(project_id,update_time) VALUES(?,?)", project, System.currentTimeMillis());
        jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE", project);
        validateCreation(project, actor, type, settings, initialMembers);
        var existing = jdbc.queryForList("SELECT * FROM plan_review_operation WHERE project_id=? AND source_kind=? AND operation_type=? AND request_key=? FOR UPDATE", project, sourceKind, type, key);
        String fingerprint = hash(payload);
        String credential=credentialReference();
        validateCredential(project,actor);importParent(project,actor,key);
        if (!existing.isEmpty()) {
            var operation = existing.getFirst();
            if (!actor.equals(operation.get("executing_actor"))) throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE", 404, "操作不可访问");
            if(!Objects.equals(credential,operation.get("executing_credential_id")))throw new PlanReviewFailure("CREDENTIAL_CONTEXT_REQUIRED",403,"原操作执行凭据上下文不匹配");
            if (!fingerprint.equals(operation.get("payload_hash"))) throw new PlanReviewFailure("REQUEST_KEY_CONFLICT", 409, "同一请求标识不能用于不同的创建参数");
            if ("TERMINATED".equals(operation.get("state"))) throw new PlanReviewFailure("OPERATION_TERMINATED", 409, "原操作已终止，不会重新创建对象");
            return reservation(operation);
        }
        if("PLAN_SERVICE".equals(sourceKind)&&"CREATE_PAIR".equals(type)){
            var request=JSON.parseObject(payload,io.metersphere.plan.dto.request.TestPlanCreateRequest.class);
            if(!project.equals(request.getProjectId()))throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"原创建目标项目不匹配");
            requirePlanDestination(project,request.getGroupId(),request.getModuleId(),request.getType(),actor);
        }
        String operation = uuid(), plan = uuid(), review = "COPY_GROUP".equals(type) ? null : uuid();
        long now = System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO plan_review_operation(id,project_id,source_kind,operation_type,request_key,payload_hash,request_json,
                original_actor,executing_actor,plan_id,review_id,initial_member_count,stage,state,create_time,update_time)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'VALIDATED','PENDING',?,?)
                """, operation, project, sourceKind, type, key, fingerprint, payload, actor, actor, plan, review, initialMembers, now, now);
        jdbc.update("UPDATE plan_review_operation SET executing_credential_id=?,original_credential_id=? WHERE id=?",credential,credential,operation);
        return new Reservation(operation, plan, review, "PENDING");
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void complete(Reservation reservation, String project, String planName, String actor,
                         PlanReviewSettings originalSettings) {
        jdbc.queryForMap("SELECT * FROM plan_review_grant WHERE project_id=? FOR UPDATE", project);
        var operation = jdbc.queryForMap("SELECT * FROM plan_review_operation WHERE id=? AND project_id=? FOR UPDATE", reservation.id(), project);
        if (!actor.equals(operation.get("executing_actor")) || !Objects.equals(credentialReference(),operation.get("executing_credential_id")) || "TERMINATED".equals(operation.get("state"))) {
            throw new PlanReviewFailure("OPERATION_TERMINATED", 409, "原操作不可继续");
        }
        validateCredential(project,actor);
        if ("COPY_GROUP".equals(operation.get("operation_type"))) {
            validateCreation(project, actor, "COPY_GROUP", originalSettings, 0);
            jdbc.update("UPDATE plan_review_operation SET stage='COMPLETED',state='COMPLETED',revision=revision+1,update_time=? WHERE id=?", System.currentTimeMillis(), reservation.id());
            return;
        }
        PlanReviewSettings settings = originalSettings.forPlanName(planName);
        validate(project, actor, settings, effectiveMembers(reservation.planId(), project));
        var existing = jdbc.queryForList("SELECT project_id FROM case_review WHERE id=?", String.class, reservation.reviewId());
        if (existing.isEmpty()) {
            var request = new CaseReviewRequest();
            request.setProjectId(project); request.setName(settings.name()); request.setModuleId(settings.moduleId());
            request.setReviewers(settings.reviewers()); request.setReviewPassRule(settings.reviewPassRule());
            var selection = new BaseAssociateCaseRequest();
            selection.setProjectId(project); selection.setSelectIds(List.of()); selection.setReviewers(settings.reviewers());
            request.setBaseAssociateCaseRequest(selection);
            reviews.addReservedCaseReview(request, actor, reservation.reviewId());
        } else if (existing.size() != 1 || !project.equals(existing.getFirst())) {
            throw new PlanReviewFailure("RESERVATION_CONFLICT", 409, "创建预留已冲突，请查看原操作");
        }
        relations.linkCreatedPair(reservation.id(), actor);
    }

    /** Only an approved migration may leave a real source plan and reserve its originally missing review. */
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void completeLegacyImportReview(io.metersphere.plan.dto.request.TestPlanCreateRequest request,String actor){
        String project=request.getProjectId();importParent(project,actor,request.getRequestKey());
        var reservation=existingReservation(project,actor,request.getRequestKey(),JSON.toJSONString(request));
        var row=jdbc.queryForMap("SELECT * FROM plan_review_operation WHERE id=? FOR UPDATE",reservation.id());
        if(!"LEGACY_PLAN_CREATED".equals(row.get("stage")))throw new PlanReviewFailure("LEGACY_STAGE_CHANGED",409,"原计划保留步骤已经变化，不会重复创建评审");
        var plans=jdbc.queryForList("SELECT id,project_id,type,status,name,group_id,module_id FROM test_plan WHERE id=? AND project_id=? FOR UPDATE",reservation.planId(),project);
        if(plans.size()!=1||!"TEST_PLAN".equals(plans.getFirst().get("type"))||!PlanReviewPolicy.State.parse((String)planStates.resolve(plans.getFirst()).get("status")).editable())throw new PlanReviewFailure("RESERVED_OBJECT_MISSING",409,"原计划不可用或已锁定，不会重建已删除对象");
        requirePlanDestination(project,(String)plans.getFirst().get("group_id"),(String)plans.getFirst().get("module_id"),"TEST_PLAN",actor,false);
        if(!jdbc.queryForList("SELECT id FROM case_review WHERE id=? FOR UPDATE",reservation.reviewId()).isEmpty()||!jdbc.queryForList("SELECT id FROM plan_review_relation WHERE active_plan_id=? FOR UPDATE",reservation.planId()).isEmpty())throw new PlanReviewFailure("RESERVATION_CONFLICT",409,"原预留已被其他对象或关联占用");
        complete(reservation,project,(String)plans.getFirst().get("name"),actor,request.getReviewSettings());
    }

    public static String childKey(String parentKey, String sourceId) {
        if (parentKey == null || parentKey.isBlank() || parentKey.length() > 128) throw new PlanReviewFailure("REQUEST_KEY_REQUIRED", 400, "缺少有效复制请求标识");
        return hash(parentKey + ":" + sourceId);
    }

    private static Reservation reservation(Map<String, Object> row) {
        return new Reservation((String) row.get("id"), (String) row.get("plan_id"), (String) row.get("review_id"), (String) row.get("state"));
    }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
