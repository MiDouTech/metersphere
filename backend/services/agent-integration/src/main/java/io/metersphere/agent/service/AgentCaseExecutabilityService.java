package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AgentCaseExecutabilityService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(AgentCaseExecutabilityService.class);
    private static final String CHECKER_VERSION=AgentCaseContentReader.CHECKER_VERSION;
    private static final Set<String> RISK_LEVELS=Set.of("LOW","MEDIUM","HIGH");
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentProjectService projects;
    @Resource private TestAssetVersionService versions;
    @Resource private AgentCaseContentReader contentReader;

    @Transactional(rollbackFor=Exception.class)
    public AgentCaseExecutabilityDTO save(AgentCaseExecutabilityConfigRequest request) {
        String projectId=projects.resolveProjectId(request.getProjectId());
        requireEnvironment(projectId,request.getEnvironmentProfileId());
        String caseId=resolveCaseId(projectId,request.getCaseId());
        List<String> pages=distinct(request.getPageObjectIds());
        List<String> datasets=distinct(request.getDatasetIds());
        String risk=StringUtils.upperCase(StringUtils.defaultIfBlank(request.getRiskLevel(),"LOW"));
        if(!RISK_LEVELS.contains(risk))throw new MSException("CASE_EXECUTABILITY_RISK_INVALID");
        long now=System.currentTimeMillis();String user=SessionUtils.getUserId();
        List<Map<String,Object>> existing=jdbc.queryForList("SELECT id,version FROM ai_case_executability_config WHERE project_id=? AND case_id=? AND environment_profile_id=?",projectId,caseId,request.getEnvironmentProfileId());
        if(existing.isEmpty()){
            jdbc.update("INSERT INTO ai_case_executability_config(id,organization_id,project_id,case_id,environment_profile_id,credential_role,page_object_ids,dataset_ids,business_flow_id,risk_level,automation_readiness,missing_items,last_checked_at,checker_version,version,create_user,update_user,create_time,update_time) SELECT ?,p.organization_id,?,?,?,?,?,?,?,?, 'NOT_READY','[]',NULL,?,0,?,?,?,? FROM project p WHERE p.id=?",
                    IDGenerator.nextStr(),projectId,caseId,request.getEnvironmentProfileId(),StringUtils.trimToNull(request.getCredentialRole()),JSON.toJSONString(pages),JSON.toJSONString(datasets),StringUtils.trimToNull(request.getBusinessFlowId()),risk,CHECKER_VERSION,user,user,now,now,projectId);
        } else {
            if(request.getVersion()==null||!Objects.equals(((Number)existing.getFirst().get("version")).intValue(),request.getVersion()))throw new MSException("CASE_EXECUTABILITY_VERSION_CONFLICT");
            int changed=jdbc.update("UPDATE ai_case_executability_config SET credential_role=?,page_object_ids=?,dataset_ids=?,business_flow_id=?,risk_level=?,automation_readiness='NOT_READY',missing_items='[]',last_checked_at=NULL,checker_version=?,version=version+1,update_user=?,update_time=? WHERE id=? AND version=?",
                    StringUtils.trimToNull(request.getCredentialRole()),JSON.toJSONString(pages),JSON.toJSONString(datasets),StringUtils.trimToNull(request.getBusinessFlowId()),risk,CHECKER_VERSION,user,now,existing.getFirst().get("id"),request.getVersion());
            if(changed!=1)throw new MSException("CASE_EXECUTABILITY_VERSION_CONFLICT");
        }
        return check(caseId,request.getEnvironmentProfileId());
    }

    @Transactional(rollbackFor=Exception.class)
    public AgentCaseExecutabilityDTO check(String caseId,String environmentProfileId){
        if(StringUtils.isAnyBlank(caseId,environmentProfileId))throw new MSException("CASE_EXECUTABILITY_REQUEST_INVALID");
        Map<String,Object> environment=requireEnvironment(null,environmentProfileId);String projectId=(String)environment.get("project_id");
        projects.resolveProjectId(projectId);
        return evaluate(findOrCreate(projectId,resolveCaseId(projectId,caseId),environmentProfileId),environment,true);
    }

    @Transactional(rollbackFor=Exception.class)
    public List<AgentCaseExecutabilityDTO> batchCheck(AgentCaseExecutabilityRequest request){
        String projectId=projects.resolveProjectId(request.getProjectId());
        Map<String,Object> environment=requireEnvironment(projectId,request.getEnvironmentProfileId());
        List<String> ids=new LinkedHashSet<>(request.getCaseIds()).stream().map(id->resolveCaseId(projectId,id)).distinct().toList();
        List<AgentCaseExecutabilityDTO> result=new ArrayList<>();
        for(String id:ids) result.add(evaluate(findOrCreate(projectId,id,request.getEnvironmentProfileId()),environment,true));
        return result;
    }

    private AgentCaseExecutabilityDTO evaluate(Map<String,Object> config,Map<String,Object> environment,boolean persist){
        AgentCaseExecutabilityDTO result=map(config);
        List<String> missing=dependencyIssues(config,environment);
        List<AgentCaseContentIssue> issues=new ArrayList<>();
        for(String code:missing) issues.add(issue(code,result,null,null));
        String stableId=jdbc.queryForObject("SELECT COALESCE(NULLIF(ref_id,''),id) FROM functional_case WHERE id=? AND project_id=? AND deleted=b'0'",
                String.class,result.getCaseId(),result.getProjectId());
        result.setStableAssetId(stableId);
        try {
            TestAssetVersionDTO version=versions.latestPublished(result.getProjectId(),"CASE",stableId);
            result.setCaseVersionId(version.getId());result.setContentHash(version.getContentHash());
            result.setContentSchemaVersion("case-source.v1");
            contentReader.published(version);
        } catch(AgentCaseContentReader.ContentException error) {
            missing.add(error.getMessage());issues.add(issue(error.getMessage(),result,error.field(),error.stepIndex()));
        } catch(MSException error) {
            if(!"PUBLISHED_ASSET_VERSION_NOT_FOUND".equals(error.getMessage()))throw error;
            missing.add("CASE_VERSION_NOT_PUBLISHED");issues.add(issue("CASE_VERSION_NOT_PUBLISHED",result,"/version",null));
        }
        long now=System.currentTimeMillis();
        result.setMissingItems(missing);result.setIssues(issues);result.setAutomationReadiness(calculateReadiness(missing));
        result.setCheckerVersion(CHECKER_VERSION);result.setLastCheckedAt(now);result.setVersion(result.getVersion()+1);
        if(persist) jdbc.update("UPDATE ai_case_executability_config SET automation_readiness=?,missing_items=?,last_checked_at=?,checker_version=?,version=version+1,update_time=? WHERE id=?",
                result.getAutomationReadiness(),JSON.toJSONString(missing),now,CHECKER_VERSION,now,result.getId());
        return result;
    }
    private AgentCaseContentIssue issue(String code,AgentCaseExecutabilityDTO result,String field,Integer index){
        String message=switch(code){
            case "CASE_VERSION_NOT_PUBLISHED" -> "用例尚未发布";
            case "CASE_CONTENT_MISSING" -> "用例缺少有效操作或预期";
            case "CASE_CONTENT_LIMIT_EXCEEDED" -> "用例内容超过安全读取限制";
            case "CASE_CONTENT_HASH_INVALID" -> "发布版本内容校验失败";
            case "CASE_CONTENT_FORMAT_INVALID", "CASE_CONTENT_DECODE_FAILED" -> "发布版本内容无法解析";
            default -> "环境、凭据或关联资产尚未就绪";
        };
        String suggestion=code.equals("CASE_VERSION_NOT_PUBLISHED")||code.equals("CASE_CONTENT_MISSING")
                ?"请核对用例内容并发布新的版本后重新检查":"请联系管理员核对配置或发布版本";
        String traceId=UUID.randomUUID().toString();
        LOG.warn("Case content check rejected project={} case={} version={} checker={} code={} field={} traceId={}",result.getProjectId(),result.getCaseId(),result.getCaseVersionId(),CHECKER_VERSION,code,field,traceId);
        return new AgentCaseContentIssue(code,"PREFLIGHT",result.getCaseId(),result.getCaseVersionId(),null,index,field,
                message,suggestion,false,traceId);
    }

    public String calculateReadiness(List<String> missingItems){
        if(missingItems==null||missingItems.isEmpty())return "READY";
        return missingItems.stream().allMatch(v->v.startsWith("OPTIONAL_"))?"PARTIAL":"NOT_READY";
    }

    public List<String> listMissingItems(Map<String,Object> config,Map<String,Object> environment){
        projects.resolveProjectId((String)config.get("project_id"));
        return evaluate(config,environment,false).getMissingItems();
    }

    private List<String> dependencyIssues(Map<String,Object> config,Map<String,Object> environment){
        List<String> missing=new ArrayList<>();String projectId=(String)config.get("project_id");
        if(!projectId.equals(environment.get("project_id")))throw new MSException("ENVIRONMENT_PROFILE_NOT_FOUND");
        Object enabled=environment.get("enabled");
        if(!(Boolean.TRUE.equals(enabled)||(enabled instanceof Number n&&n.intValue()==1)))missing.add("ENVIRONMENT_PROFILE_DISABLED");
        String role=(String)config.get("credential_role");
        if(StringUtils.isNotBlank(role))count(missing,"CREDENTIAL_ROLE_UNAVAILABLE","SELECT COUNT(1) FROM ai_credential_reference WHERE project_id=? AND environment_id=? AND business_role=? AND enabled=b'1' AND status='ACTIVE' AND (expires_at IS NULL OR expires_at>?)",projectId,environment.get("environment_id"),role,System.currentTimeMillis());
        for(String id:jsonList(config.get("page_object_ids")))count(missing,"PAGE_OBJECT_NOT_PUBLISHED:"+id,"SELECT COUNT(1) FROM ai_page_object WHERE id=? AND project_id=? AND status='PUBLISHED'",id,projectId);
        for(String id:jsonList(config.get("dataset_ids")))count(missing,"DATASET_NOT_PUBLISHED:"+id,"SELECT COUNT(1) FROM test_asset_version WHERE project_id=? AND asset_type='DATASET' AND asset_id=? AND status='PUBLISHED'",projectId,id);
        if(config.get("business_flow_id")!=null)count(missing,"BUSINESS_FLOW_NOT_PUBLISHED","SELECT COUNT(1) FROM ai_business_flow WHERE id=? AND project_id=? AND status='PUBLISHED'",config.get("business_flow_id"),projectId);
        return missing;
    }

    private void count(List<String> missing,String code,String sql,Object... args){Integer n=jdbc.queryForObject(sql,Integer.class,args);if(n==null||n==0)missing.add(code);}
    private Map<String,Object> requireEnvironment(String projectId,String id){List<Map<String,Object>> rows=projectId==null?jdbc.queryForList("SELECT * FROM ai_environment_execution_profile WHERE id=?",id):jdbc.queryForList("SELECT * FROM ai_environment_execution_profile WHERE id=? AND project_id=?",id,projectId);if(rows.isEmpty())throw new MSException("ENVIRONMENT_PROFILE_NOT_FOUND");return rows.getFirst();}
    private String resolveCaseId(String projectId,String supplied){
        List<String> ids=jdbc.queryForList("SELECT id FROM functional_case WHERE project_id=? AND (id=? OR ref_id=?) AND deleted=b'0' ORDER BY (id=?) DESC,latest DESC,update_time DESC,id DESC LIMIT 1",String.class,projectId,supplied,supplied,supplied);
        if(ids.isEmpty())throw new MSException("CASE_NOT_FOUND");return ids.getFirst();
    }
    private Map<String,Object> findOrCreate(String projectId,String caseId,String environmentProfileId){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM ai_case_executability_config WHERE project_id=? AND case_id=? AND environment_profile_id=?",projectId,caseId,environmentProfileId);if(!rows.isEmpty())return rows.getFirst();long now=System.currentTimeMillis();String id=IDGenerator.nextStr();jdbc.update("INSERT INTO ai_case_executability_config(id,organization_id,project_id,case_id,environment_profile_id,page_object_ids,dataset_ids,risk_level,automation_readiness,missing_items,checker_version,version,create_user,update_user,create_time,update_time) SELECT ?,p.organization_id,?,?,?,'[]','[]','LOW','NOT_READY','[]',?,0,?,?,?,? FROM project p WHERE p.id=?",id,projectId,caseId,environmentProfileId,CHECKER_VERSION,SessionUtils.getUserId(),SessionUtils.getUserId(),now,now,projectId);return jdbc.queryForMap("SELECT * FROM ai_case_executability_config WHERE id=?",id);}
    private List<String> distinct(List<String> values){return values==null?List.of():values.stream().filter(StringUtils::isNotBlank).map(String::trim).distinct().toList();}
    @SuppressWarnings("unchecked") private List<String> jsonList(Object value){if(value==null)return List.of();return JSON.parseArray(String.valueOf(value),String.class);}
    private AgentCaseExecutabilityDTO map(Map<String,Object> row){AgentCaseExecutabilityDTO d=new AgentCaseExecutabilityDTO();d.setId((String)row.get("id"));d.setProjectId((String)row.get("project_id"));d.setCaseId((String)row.get("case_id"));d.setEnvironmentProfileId((String)row.get("environment_profile_id"));d.setAutomationReadiness((String)row.get("automation_readiness"));d.setCredentialRole((String)row.get("credential_role"));d.setPageObjectIds(jsonList(row.get("page_object_ids")));d.setDatasetIds(jsonList(row.get("dataset_ids")));d.setBusinessFlowId((String)row.get("business_flow_id"));d.setRiskLevel((String)row.get("risk_level"));d.setMissingItems(jsonList(row.get("missing_items")));d.setLastCheckedAt(row.get("last_checked_at")==null?null:((Number)row.get("last_checked_at")).longValue());d.setCheckerVersion((String)row.get("checker_version"));d.setVersion(((Number)row.get("version")).intValue());return d;}
}
