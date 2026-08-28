package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AgentLoginProfileService {
    private static final Set<String> LOGIN_TYPES=Set.of("FORM","TOKEN");
    private static final Set<String> MFA_POLICIES=Set.of("BLOCK","CHECKPOINT");
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentProjectService projects;
    @Resource private AgentEnvironmentProfileService environments;
    @Resource(name = "agentWebExecutionContractValidator") private AgentWebExecutionContractValidator validator;

    public List<AgentLoginProfileDTO> list(String projectId){String p=projects.resolveProjectId(projectId);return jdbc.query("SELECT * FROM ai_login_profile WHERE project_id=? ORDER BY update_time DESC",this::map,p);}
    public AgentLoginProfileDTO get(String id){List<AgentLoginProfileDTO> rows=jdbc.query("SELECT * FROM ai_login_profile WHERE id=?",this::map,id);if(rows.isEmpty())throw new MSException("LOGIN_PROFILE_NOT_FOUND");projects.resolveProjectId(rows.getFirst().getProjectId());return rows.getFirst();}
    @Transactional(rollbackFor=Exception.class) public AgentLoginProfileDTO create(AgentLoginProfileRequest r){String p=projects.resolveProjectId(r.getProjectId());validate(r,p);long now=System.currentTimeMillis();String id=IDGenerator.nextStr();String org=jdbc.queryForObject("SELECT organization_id FROM project WHERE id=?",String.class,p);jdbc.update("INSERT INTO ai_login_profile(id,organization_id,project_id,environment_profile_id,name,login_type,login_url,username_locator,password_locator,submit_locator,success_assertion,session_validation,mfa_policy,timeout_ms,version,enabled,create_user,update_user,create_time,update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?,?,?,?)",id,org,p,r.getEnvironmentProfileId(),r.getName().trim(),r.getLoginType().toUpperCase(),r.getLoginUrl().trim(),normalize(r.getUsernameLocator()),normalize(r.getPasswordLocator()),normalize(r.getSubmitLocator()),normalize(r.getSuccessAssertion()),normalizeNullable(r.getSessionValidation()),r.getMfaPolicy().toUpperCase(),r.getTimeoutMs()==null?15000:r.getTimeoutMs(),r.getEnabled()==null||r.getEnabled(),SessionUtils.getUserId(),SessionUtils.getUserId(),now,now);return get(id);}
    @Transactional(rollbackFor=Exception.class) public AgentLoginProfileDTO update(String id,AgentLoginProfileRequest r){AgentLoginProfileDTO old=get(id);String p=projects.resolveProjectId(r.getProjectId());if(!p.equals(old.getProjectId()))throw new MSException("LOGIN_PROFILE_PROJECT_MISMATCH");validate(r,p);int changed=jdbc.update("UPDATE ai_login_profile SET environment_profile_id=?,name=?,login_type=?,login_url=?,username_locator=?,password_locator=?,submit_locator=?,success_assertion=?,session_validation=?,mfa_policy=?,timeout_ms=?,enabled=?,version=version+1,update_user=?,update_time=? WHERE id=? AND version=?",r.getEnvironmentProfileId(),r.getName().trim(),r.getLoginType().toUpperCase(),r.getLoginUrl().trim(),normalize(r.getUsernameLocator()),normalize(r.getPasswordLocator()),normalize(r.getSubmitLocator()),normalize(r.getSuccessAssertion()),normalizeNullable(r.getSessionValidation()),r.getMfaPolicy().toUpperCase(),r.getTimeoutMs()==null?15000:r.getTimeoutMs(),r.getEnabled()==null||r.getEnabled(),SessionUtils.getUserId(),System.currentTimeMillis(),id,r.getVersion());if(changed!=1)throw new MSException("LOGIN_PROFILE_VERSION_CONFLICT");return get(id);}
    @Transactional(rollbackFor=Exception.class) public AgentLoginProfileDTO setEnabled(String id,boolean enabled){AgentLoginProfileDTO p=get(id);if(jdbc.update("UPDATE ai_login_profile SET enabled=?,version=version+1,update_user=?,update_time=? WHERE id=? AND version=?",enabled,SessionUtils.getUserId(),System.currentTimeMillis(),id,p.getVersion())!=1)throw new MSException("LOGIN_PROFILE_VERSION_CONFLICT");return get(id);}
    public String freeze(String id,String projectId){AgentLoginProfileDTO p=get(id);if(!projectId.equals(p.getProjectId())||!Boolean.TRUE.equals(p.getEnabled()))throw new MSException("LOGIN_PROFILE_NOT_USABLE");Map<String,Object> snapshot=new java.util.LinkedHashMap<>();snapshot.put("id",p.getId());snapshot.put("version",p.getVersion());snapshot.put("loginType",p.getLoginType());snapshot.put("loginUrl",p.getLoginUrl());snapshot.put("usernameLocator",JSON.parseObject(p.getUsernameLocator()));snapshot.put("passwordLocator",JSON.parseObject(p.getPasswordLocator()));snapshot.put("submitLocator",JSON.parseObject(p.getSubmitLocator()));snapshot.put("successAssertion",JSON.parseObject(p.getSuccessAssertion()));snapshot.put("sessionValidation",StringUtils.isBlank(p.getSessionValidation())?Map.of():JSON.parseObject(p.getSessionValidation()));snapshot.put("mfaPolicy",p.getMfaPolicy());snapshot.put("timeoutMs",p.getTimeoutMs());return JSON.toJSONString(snapshot);}
    private void validate(AgentLoginProfileRequest r,String projectId){AgentEnvironmentProfileDTO env=environments.resolveForTask(r.getEnvironmentProfileId(),projectId);environments.assertTargetAllowed(env,r.getLoginUrl());if(!LOGIN_TYPES.contains(r.getLoginType().toUpperCase()))throw new MSException("LOGIN_TYPE_INVALID");if(!MFA_POLICIES.contains(r.getMfaPolicy().toUpperCase()))throw new MSException("MFA_POLICY_INVALID");validateLocator(r.getUsernameLocator());validateLocator(r.getPasswordLocator());validateLocator(r.getSubmitLocator());validateAssertion(r.getSuccessAssertion());if(StringUtils.isNotBlank(r.getSessionValidation()))validateAssertion(r.getSessionValidation());}
    private void validateLocator(String json){try{validator.validateLocator(JSON.parseObject(json,AgentWebLocatorDTO.class));}catch(Exception e){throw new MSException("LOGIN_LOCATOR_INVALID");}}
    private void validateAssertion(String json){try{validator.validateAssertions(List.of(JSON.parseObject(json,AgentWebAssertionDTO.class)));}catch(Exception e){throw new MSException("LOGIN_ASSERTION_INVALID");}}
    private String normalize(String json){return JSON.toJSONString(JSON.parseObject(json));} private String normalizeNullable(String json){return StringUtils.isBlank(json)?null:normalize(json);}
    private AgentLoginProfileDTO map(java.sql.ResultSet rs,int n)throws java.sql.SQLException{AgentLoginProfileDTO d=new AgentLoginProfileDTO();d.setId(rs.getString("id"));d.setOrganizationId(rs.getString("organization_id"));d.setProjectId(rs.getString("project_id"));d.setEnvironmentProfileId(rs.getString("environment_profile_id"));d.setName(rs.getString("name"));d.setLoginType(rs.getString("login_type"));d.setLoginUrl(rs.getString("login_url"));d.setUsernameLocator(rs.getString("username_locator"));d.setPasswordLocator(rs.getString("password_locator"));d.setSubmitLocator(rs.getString("submit_locator"));d.setSuccessAssertion(rs.getString("success_assertion"));d.setSessionValidation(rs.getString("session_validation"));d.setMfaPolicy(rs.getString("mfa_policy"));d.setTimeoutMs(rs.getInt("timeout_ms"));d.setVersion(rs.getInt("version"));d.setEnabled(rs.getBoolean("enabled"));d.setCreateTime(rs.getLong("create_time"));d.setUpdateTime(rs.getLong("update_time"));return d;}
}
