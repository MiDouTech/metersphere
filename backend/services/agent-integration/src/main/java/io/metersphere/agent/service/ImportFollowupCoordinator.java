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
        var groups=jdbc.queryForList("SELECT g.id,g.revision,g.plan_id planId,g.review_id reviewId,p.name planName,r.name reviewName "
                + "FROM functional_import_followup_group g JOIN test_plan p ON BINARY p.id=BINARY g.plan_id JOIN case_review r ON BINARY r.id=BINARY g.review_id "
                + "WHERE g.project_id=? AND p.status<>'ARCHIVED' AND r.status NOT IN ('ARCHIVED','COMPLETED') ORDER BY g.create_time DESC,g.id LIMIT 100",project);
        boolean allowed=access.userHasPermission(user,project,PermissionConstants.TEST_PLAN_READ_ADD)
                && access.userHasPermission(user,project,PermissionConstants.TEST_PLAN_READ_ASSOCIATION)
                && access.userHasPermission(user,project,PermissionConstants.CASE_REVIEW_READ_ADD)
                && access.userHasPermission(user,project,PermissionConstants.CASE_REVIEW_RELEVANCE);
        return Map.of("projectId",project,"groups",(access.userHasPermission(user,project,PermissionConstants.TEST_PLAN_READ) && access.userHasPermission(user,project,PermissionConstants.CASE_REVIEW_READ)) ? groups : List.of(),"canCreate",allowed);
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
            var existing=jdbc.queryForList("SELECT request_json FROM functional_import_followup WHERE job_id=?",id);
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
                var groups=jdbc.queryForList("SELECT * FROM functional_import_followup_group WHERE id=? AND project_id=? FOR UPDATE",request.getTargetGroupId(),project);
                if(groups.size()!=1)throw new MSException("所选导入关联组已失效");
                var g=groups.getFirst();
                if(request.getExpectedGroupRevision()==null || ((Number)g.get("revision")).longValue()!=request.getExpectedGroupRevision())throw new MSException("关联组已更新，请刷新后重新选择");
                group=(String)g.get("id"); plan=(String)g.get("plan_id");review=(String)g.get("review_id");
                validateTargets(project,plan,review);
                var currentReviewers = targetReviewers(project,review);
                if(request.getReviewers()!=null && !request.getReviewers().isEmpty() && !new HashSet<>(request.getReviewers()).equals(new HashSet<>(currentReviewers)))
                    throw new MSException("AGENT_VALIDATION_ERROR");
                jdbc.update("UPDATE functional_import_followup_group SET revision=revision+1 WHERE id=?",group);
            }
            long now=System.currentTimeMillis();
            jdbc.update("INSERT INTO functional_import_followup(job_id,project_id,user_id,policy,request_json,group_id,plan_id,review_id,state,create_time,update_time,source_kind) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    id,project,user,request.getPolicy(),payload,group,plan,review,"IMPORT_ONLY".equals(request.getPolicy())?"SKIPPED":"PENDING",now,now,job.get("source_kind"));
        });
    }

    private void permissions(String project,String user,List<String> reviewers,String policy) {
        List<String> required=new ArrayList<>(List.of(PermissionConstants.TEST_PLAN_READ_ASSOCIATION,PermissionConstants.CASE_REVIEW_RELEVANCE));
        if("CREATE_NEW_PAIR".equals(policy))required.addAll(List.of(PermissionConstants.TEST_PLAN_READ_ADD,PermissionConstants.CASE_REVIEW_READ_ADD));
        for(String permission:required)
            if(!access.userHasPermission(user,project,permission))throw new MSException("当前账号缺少创建或关联计划/评审的权限，可选择仅导入");
        if("CREATE_NEW_PAIR".equals(policy)) access.reviewers(project,reviewers);
    }

    private List<String> targetReviewers(String project,String review) {
        var reviewers = jdbc.queryForList("SELECT u.user_id FROM case_review_user u JOIN case_review r ON BINARY r.id=BINARY u.review_id WHERE r.id=? AND r.project_id=? ORDER BY u.user_id",String.class,review,project);
        access.reviewers(project,reviewers);
        return reviewers;
    }

    private void validateTargets(String project,String plan,String review) {
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM test_plan p JOIN case_review r ON r.id=? AND BINARY r.project_id=BINARY p.project_id WHERE p.id=? AND p.project_id=? AND p.status<>'ARCHIVED' AND r.status NOT IN ('ARCHIVED','COMPLETED')",Long.class,review,plan,project);
        if(count==null||count!=1)throw new MSException("目标计划或评审已删除、归档或结束，请重新选择关联目标");
    }

    public Map<String,Object> status(String id,String user) { return status(id,user,"WEB"); }
    public Map<String,Object> status(String id,String user,String token) {
        var job=owned(id,user,token);
        var rows=jdbc.queryForList("SELECT f.state,f.error_message message,f.plan_id planId,f.review_id reviewId,p.name planName,r.name reviewName "
                + "FROM functional_import_followup f LEFT JOIN test_plan p ON BINARY p.id=BINARY f.plan_id LEFT JOIN case_review r ON BINARY r.id=BINARY f.review_id WHERE f.job_id=?",id);
        if(rows.isEmpty())return Map.of("state","LEGACY");
        var result=new LinkedHashMap<>(rows.getFirst());String project=(String)job.get("project_id");
        if(!access.userHasPermission(user,project,PermissionConstants.TEST_PLAN_READ)){result.remove("planId");result.remove("planName");}
        if(!access.userHasPermission(user,project,PermissionConstants.CASE_REVIEW_READ)){result.remove("reviewId");result.remove("reviewName");}
        return result;
    }

    public void retry(String id,String user) { retry(id,user,"WEB"); }
    public void retry(String id,String user,String token) {
        owned(id,user,token);
        jdbc.update("UPDATE functional_import_followup SET state='PENDING',error_message=NULL,update_time=? WHERE job_id=? AND user_id=? AND state IN ('FAILED','SUCCEEDED')",System.currentTimeMillis(),id,user);
    }

    @Scheduled(fixedDelay=3000,initialDelay=30000,scheduler="functionalImportEffectsScheduler")
    public void drain() {
        var due=jdbc.queryForList("SELECT f.job_id FROM functional_import_followup f LEFT JOIN functional_import_job j ON BINARY j.id=BINARY f.job_id AND f.source_kind='FILE' LEFT JOIN default_hub_sync_job a ON BINARY a.id=BINARY f.job_id AND f.source_kind='ASSET' WHERE f.state='PENDING' AND (j.state IN ('COMPLETED','PARTIAL','STOPPED') OR a.status IN ('SUCCESS','PARTIAL_SUCCESS','FAILED')) ORDER BY f.update_time,f.job_id LIMIT 1",String.class);
        if(due.isEmpty())return;
        String id=due.getFirst();
        try { tx.executeWithoutResult(status->process(id)); }
        catch(Exception failure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Import association failed job={}",id,failure);
            jdbc.update("UPDATE functional_import_followup SET state='FAILED',error_message=?,update_time=? WHERE job_id=? AND state='PENDING'",
                    failure instanceof MSException?org.apache.commons.lang3.StringUtils.left(failure.getMessage(),900):"关联未完成，请核对目标、权限和评审人后重试",System.currentTimeMillis(),id);
        }
    }

    private void process(String id) {
        var rows=jdbc.queryForList("SELECT * FROM functional_import_followup WHERE job_id=? AND state='PENDING' FOR UPDATE SKIP LOCKED",id);
        if(rows.isEmpty())return;
        var row=rows.getFirst();String user=(String)row.get("user_id"),project=(String)row.get("project_id");
        String token="ASSET".equals(row.get("source_kind"))?"WEB":jdbc.queryForObject("SELECT token_id FROM functional_import_job WHERE id=? AND user_id=?",String.class,id,user);
        var job=owned(id,user,token);checkTokenPolicy(job,user);var request=JSON.parseObject((String)row.get("request_json"),ImportFollowupRequest.class);
        permissions(project,user,request.getReviewers(),request.getPolicy());
        if("APPEND_EXISTING".equals(request.getPolicy())) {
            validateTargets(project,(String)row.get("plan_id"),(String)row.get("review_id"));
            request.setReviewers(targetReviewers(project,(String)row.get("review_id")));
        }
        if("ASSET".equals(row.get("source_kind")))jdbc.update("INSERT IGNORE INTO functional_import_followup_item(job_id,case_id) SELECT job_id,target_case_id FROM case_asset_import_result WHERE job_id=? AND status='SUCCESS' AND target_case_id IS NOT NULL",id);
        else jdbc.update("INSERT IGNORE INTO functional_import_followup_item(job_id,case_id) SELECT job_id,target_id FROM functional_import_item WHERE job_id=? AND state='SUCCESS' AND target_id IS NOT NULL",id);
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup_item WHERE job_id=?",Long.class,id);
        if(total==null||total==0){jdbc.update("UPDATE functional_import_followup SET state='SUCCEEDED',update_time=? WHERE job_id=?",System.currentTimeMillis(),id);return;}
        String plan=(String)row.get("plan_id"),review=(String)row.get("review_id");
        if(plan==null) {
            var create=new TestPlanCreateRequest();create.setProjectId(project);create.setName(name(request.getPlanName(),String.valueOf(job.get("source_name")),"测试计划",id));
            plan=plans.add(create,user,"/ai/case-imports/followup","POST").getId();
            jdbc.update("UPDATE functional_import_followup SET plan_id=?,update_time=? WHERE job_id=?",plan,System.currentTimeMillis(),id);return;
        }
        if(review==null) {
            var create=new CaseReviewRequest();create.setProjectId(project);create.setModuleId("root");create.setName(name(request.getReviewName(),String.valueOf(job.get("source_name")),"评审",id));
            create.setReviewPassRule(request.getReviewPassRule());create.setReviewers(request.getReviewers());
            var selection=new BaseAssociateCaseRequest();selection.setProjectId(project);selection.setSelectIds(List.of());selection.setReviewers(request.getReviewers());create.setBaseAssociateCaseRequest(selection);
            review=reviews.addCaseReview(create,user).getId();jdbc.update("UPDATE functional_import_followup SET review_id=?,update_time=? WHERE job_id=?",review,System.currentTimeMillis(),id);return;
        }
        validateTargets(project,plan,review);
        jdbc.update("INSERT IGNORE INTO functional_import_followup_group(id,project_id,plan_id,review_id,create_user,create_time) VALUES(?,?,?,?,?,?)",row.get("group_id"),project,plan,review,user,System.currentTimeMillis());
        var ids=jdbc.queryForList("SELECT case_id FROM functional_import_followup_item WHERE job_id=? AND (plan_done=0 OR review_done=0) ORDER BY case_id LIMIT 100",String.class,id);
        if(ids.isEmpty()){jdbc.update("UPDATE functional_import_followup SET state='SUCCEEDED',update_time=? WHERE job_id=?",System.currentTimeMillis(),id);return;}
        access.cases(project,ids);
        var session=SessionUser.fromUser(users.getUserDTO(user),"");session.setLastProjectId(project);
        session.setLastOrganizationId(jdbc.queryForObject("SELECT organization_id FROM project WHERE id=?",String.class,project));
        var associate=new TestPlanFunctionalCaseAssociateRequest();associate.setTestPlanId(plan);associate.setProjectId(project);associate.setSelectIds(ids);planCases.associateCases(associate,session);
        var selection=new BaseAssociateCaseRequest();selection.setProjectId(project);selection.setSelectIds(ids);selection.setReviewers(request.getReviewers());
        var reviewRequest=new CaseReviewAssociateRequest();reviewRequest.setProjectId(project);reviewRequest.setReviewId(review);reviewRequest.setReviewers(request.getReviewers());reviewRequest.setBaseAssociateCaseRequest(selection);reviews.associateCase(reviewRequest,user);
        for(String caseId:ids)jdbc.update("UPDATE functional_import_followup_item SET plan_done=1,review_done=1 WHERE job_id=? AND case_id=?",id,caseId);
        jdbc.update("UPDATE functional_import_followup SET update_time=? WHERE job_id=?",System.currentTimeMillis(),id);
    }

    private void checkTokenPolicy(Map<String,Object> job,String user) {
        String token=Objects.toString(job.get("token_id"),"WEB");if("WEB".equals(token))return;
        var tokens=jdbc.query("SELECT * FROM agent_token WHERE id=? AND user_id=?",org.springframework.jdbc.core.BeanPropertyRowMapper.newInstance(io.metersphere.system.domain.AgentToken.class),token,user);
        if(tokens.size()!=1)throw AgentBusinessAccess.denied();
        var previous=io.metersphere.agent.security.AgentTokenContext.get();
        try {
            io.metersphere.agent.security.AgentTokenContext.set(tokens.getFirst());
            access.project((String)job.get("project_id"),PermissionConstants.TEST_PLAN_READ_ASSOCIATION,true,io.metersphere.agent.constants.AgentTokenScope.PLAN_WRITE);
            access.project((String)job.get("project_id"),PermissionConstants.CASE_REVIEW_RELEVANCE,true,io.metersphere.agent.constants.AgentTokenScope.REVIEW_WRITE);
        } finally {
            if(previous==null)io.metersphere.agent.security.AgentTokenContext.clear();else io.metersphere.agent.security.AgentTokenContext.set(previous);
        }
    }

    private static String name(String chosen,String file,String suffix,String id) {
        return org.apache.commons.lang3.StringUtils.left(chosen==null||chosen.isBlank()?file+"-"+suffix+"-"+id.substring(0,Math.min(8,id.length())):chosen.trim(),255);
    }
}
