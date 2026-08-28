package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentModelProfileDTO;
import io.metersphere.agent.dto.AgentModelProfileRequest;
import io.metersphere.agent.service.gateway.MapGatewayClient;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentModelProfileService {
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource(name = "agentProjectService") private AgentProjectService projectService;
    @Resource private ProjectMapper projectMapper;
    @Resource private MapGatewayClient gateway;

    public List<AgentModelProfileDTO> list(String projectId) {
        String resolved = projectService.resolveProjectId(projectId);
        return jdbcTemplate.query("SELECT * FROM ai_model_profile WHERE project_id=? ORDER BY update_time DESC", this::map, resolved);
    }

    public AgentModelProfileDTO get(String id) {
        List<AgentModelProfileDTO> rows = jdbcTemplate.query("SELECT * FROM ai_model_profile WHERE id=?", this::map, id);
        if (rows.isEmpty()) throw new MSException("MODEL_PROFILE_NOT_FOUND");
        projectService.resolveProjectId(rows.getFirst().getProjectId());
        return rows.getFirst();
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentModelProfileDTO create(AgentModelProfileRequest request) {
        String projectId = projectService.resolveProjectId(request.getProjectId());
        Project project = projectMapper.selectByPrimaryKey(projectId);
        if (project == null) throw new MSException("PROJECT_NOT_FOUND");
        validate(request);
        String id = IDGenerator.nextStr(); long now = System.currentTimeMillis();
        try {
            jdbcTemplate.update("""
                INSERT INTO ai_model_profile
                (id,organization_id,project_id,name,gateway_app_caller,gateway_service_key_ref,logical_model_public_id,
                 prompt_policy_id,gateway_prompt_policy_id,required_capabilities,request_timeout_ms,max_output_tokens,
                 max_cost_amount,currency,enabled,version,create_user,update_user,create_time,update_time)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, project.getOrganizationId(), projectId, request.getName().trim(), request.getGatewayAppCaller().trim(),
                    request.getGatewayServiceKeyRef().trim(), request.getLogicalModelPublicId().trim(), request.getPromptPolicyId().trim(),
                    StringUtils.trimToNull(request.getGatewayPromptPolicyId()), JSON.toJSONString(safe(request.getRequiredCapabilities())),
                    value(request.getRequestTimeoutMs(),120000), value(request.getMaxOutputTokens(),8192), request.getMaxCostAmount(),
                    StringUtils.defaultIfBlank(request.getCurrency(),"CNY"), request.getEnabled() == null || request.getEnabled(), 0,
                    SessionUtils.getUserId(), SessionUtils.getUserId(), now, now);
        } catch (DuplicateKeyException ex) { throw new MSException("MODEL_PROFILE_NAME_CONFLICT"); }
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentModelProfileDTO update(String id, AgentModelProfileRequest request) {
        AgentModelProfileDTO existing = get(id);
        if (!existing.getProjectId().equals(projectService.resolveProjectId(request.getProjectId()))) throw new MSException("MODEL_PROFILE_PROJECT_MISMATCH");
        validate(request);
        int expected = request.getVersion() == null ? existing.getVersion() : request.getVersion();
        int changed = jdbcTemplate.update("""
            UPDATE ai_model_profile SET name=?,gateway_app_caller=?,gateway_service_key_ref=?,logical_model_public_id=?,
            prompt_policy_id=?,gateway_prompt_policy_id=?,required_capabilities=?,request_timeout_ms=?,max_output_tokens=?,
            max_cost_amount=?,currency=?,enabled=?,version=version+1,last_verified_at=NULL,last_verify_status=NULL,
            last_verify_message=NULL,update_user=?,update_time=? WHERE id=? AND project_id=? AND version=?
            """, request.getName().trim(),request.getGatewayAppCaller().trim(),request.getGatewayServiceKeyRef().trim(),
                request.getLogicalModelPublicId().trim(),request.getPromptPolicyId().trim(),StringUtils.trimToNull(request.getGatewayPromptPolicyId()),
                JSON.toJSONString(safe(request.getRequiredCapabilities())),value(request.getRequestTimeoutMs(),120000),
                value(request.getMaxOutputTokens(),8192),request.getMaxCostAmount(),StringUtils.defaultIfBlank(request.getCurrency(),"CNY"),
                request.getEnabled() == null || request.getEnabled(),SessionUtils.getUserId(),System.currentTimeMillis(),id,existing.getProjectId(),expected);
        if (changed != 1) throw new MSException("MODEL_PROFILE_VERSION_CONFLICT");
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> verify(String id) {
        AgentModelProfileDTO profile = get(id); String traceId = UUID.randomUUID().toString(); long now=System.currentTimeMillis();
        String ref = serviceKeyRef(id);
        try {
            Map<String,Object> health = gateway.health(profile.getGatewayAppCaller(),ref,traceId);
            Map<String,Object> caps = gateway.capabilities(profile.getGatewayAppCaller(),ref,profile.getLogicalModelPublicId(),traceId);
            jdbcTemplate.update("UPDATE ai_model_profile SET last_verified_at=?,last_verify_status='PASSED',last_verify_message='Gateway verification passed',gateway_capability_snapshot=?,version=version+1,update_time=? WHERE id=?",
                    now,JSON.toJSONString(caps),now,id);
            return Map.of("valid",true,"health",health,"capabilities",caps,"traceId",traceId);
        } catch (RuntimeException ex) {
            jdbcTemplate.update("UPDATE ai_model_profile SET last_verified_at=?,last_verify_status='FAILED',last_verify_message='Gateway unavailable or rejected the profile',version=version+1,update_time=? WHERE id=?",now,now,id);
            return Map.of("valid",false,"code",safeCode(ex),"message","MAP Gateway verification failed","traceId",traceId);
        }
    }

    public AgentModelProfileDTO assertUsable(String id, String projectId, List<String> requiredCapabilities) {
        AgentModelProfileDTO profile=get(id);
        if (!profile.getProjectId().equals(projectId)) throw new MSException("MODEL_PROFILE_PROJECT_MISMATCH");
        if (!Boolean.TRUE.equals(profile.getEnabled())) throw new MSException("MODEL_PROFILE_DISABLED");
        if (!"PASSED".equals(profile.getLastVerifyStatus())) throw new MSException("MODEL_PROFILE_NOT_VERIFIED");
        if (!profile.getRequiredCapabilities().containsAll(safe(requiredCapabilities))) throw new MSException("MODEL_CAPABILITY_MISMATCH");
        return profile;
    }

    public String freeze(String id) {
        AgentModelProfileDTO profile=get(id);
        return JSON.toJSONString(Map.of("id",profile.getId(),"version",profile.getVersion(),"gatewayAppCaller",profile.getGatewayAppCaller(),
                "logicalModelPublicId",profile.getLogicalModelPublicId(),"promptPolicyId",profile.getPromptPolicyId(),
                "requiredCapabilities",profile.getRequiredCapabilities(),"requestTimeoutMs",profile.getRequestTimeoutMs(),
                "maxOutputTokens",profile.getMaxOutputTokens(),"currency",profile.getCurrency()));
    }

    public String serviceKeyRef(String id) {
        return jdbcTemplate.queryForObject("SELECT gateway_service_key_ref FROM ai_model_profile WHERE id=?",String.class,id);
    }

    public AgentModelProfileDTO setEnabled(String id, boolean enabled) {
        AgentModelProfileDTO p=get(id);
        if (jdbcTemplate.update("UPDATE ai_model_profile SET enabled=?,version=version+1,update_user=?,update_time=? WHERE id=? AND version=?",enabled,SessionUtils.getUserId(),System.currentTimeMillis(),id,p.getVersion())!=1)
            throw new MSException("MODEL_PROFILE_VERSION_CONFLICT");
        return get(id);
    }

    public Map<String,Object> health(String id){
        AgentModelProfileDTO profile=get(id);
        return gateway.health(profile.getGatewayAppCaller(),serviceKeyRef(id),UUID.randomUUID().toString());
    }

    public Map<String,Object> capabilities(String id){
        AgentModelProfileDTO profile=get(id);
        return gateway.capabilities(profile.getGatewayAppCaller(),serviceKeyRef(id),profile.getLogicalModelPublicId(),UUID.randomUUID().toString());
    }

    private void validate(AgentModelProfileRequest r) {
        if (!(r.getGatewayServiceKeyRef().startsWith("env://") || r.getGatewayServiceKeyRef().startsWith("vault://"))) throw new MSException("MODEL_SERVICE_KEY_REF_INVALID");
        if (r.getMaxCostAmount()!=null && r.getMaxCostAmount().signum()<0) throw new MSException("MODEL_BUDGET_INVALID");
    }
    private int value(Integer v,int d){return v==null?d:v;}
    private List<String> safe(List<String> v){return v==null?List.of():v.stream().filter(StringUtils::isNotBlank).map(String::trim).distinct().sorted().toList();}
    private String safeCode(Throwable t){String m=StringUtils.defaultString(t.getMessage());return m.matches("[A-Z0-9_]+")?m:"MAP_GATEWAY_UNAVAILABLE";}
    private AgentModelProfileDTO map(ResultSet rs,int row) throws SQLException {
        AgentModelProfileDTO d=new AgentModelProfileDTO();d.setId(rs.getString("id"));d.setOrganizationId(rs.getString("organization_id"));d.setProjectId(rs.getString("project_id"));d.setName(rs.getString("name"));d.setGatewayAppCaller(rs.getString("gateway_app_caller"));d.setLogicalModelPublicId(rs.getString("logical_model_public_id"));d.setPromptPolicyId(rs.getString("prompt_policy_id"));d.setGatewayPromptPolicyId(rs.getString("gateway_prompt_policy_id"));d.setRequiredCapabilities(Arrays.asList(JSON.parseArray(StringUtils.defaultString(rs.getString("required_capabilities"),"[]"),String.class).toArray(String[]::new)));d.setRequestTimeoutMs(rs.getInt("request_timeout_ms"));d.setMaxOutputTokens(rs.getInt("max_output_tokens"));d.setMaxCostAmount(rs.getBigDecimal("max_cost_amount"));d.setCurrency(rs.getString("currency"));d.setEnabled(rs.getBoolean("enabled"));d.setVersion(rs.getInt("version"));long lv=rs.getLong("last_verified_at");d.setLastVerifiedAt(rs.wasNull()?null:lv);d.setLastVerifyStatus(rs.getString("last_verify_status"));d.setLastVerifyMessage(rs.getString("last_verify_message"));d.setCreateTime(rs.getLong("create_time"));d.setUpdateTime(rs.getLong("update_time"));return d;
    }
}
