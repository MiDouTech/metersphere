package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCredentialReferenceDTO;
import io.metersphere.agent.dto.AgentCredentialReferenceRequest;
import io.metersphere.agent.dto.AgentCredentialVerifyResult;
import io.metersphere.agent.dto.AgentCredentialResolveRequest;
import io.metersphere.agent.dto.AgentCredentialResolveResponse;
import io.metersphere.agent.dto.AgentRunnerLeaseDTO;
import io.metersphere.agent.constants.AgentExecutorChannel;
import io.metersphere.agent.secret.ResolvedSecret;
import io.metersphere.agent.secret.SecretResolveContext;
import io.metersphere.sdk.util.JSON;
import io.metersphere.agent.secret.AgentSecretProvider;
import io.metersphere.agent.secret.AgentSecretProviderRegistry;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.spec.MGF1ParameterSpec;
import java.security.SecureRandom;

@Service
public class AgentCredentialReferenceService {
    private static final Set<String> TYPES = Set.of("USERNAME_PASSWORD", "TOKEN", "API_KEY", "OAUTH_CLIENT");
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private AgentProjectService projectService;
    @Resource private ProjectMapper projectMapper;
    @Resource private AgentSecretProviderRegistry providers;
    @Resource private AgentExecLogService auditLog;
    @Resource private AgentRunnerService runnerService;

    public List<AgentCredentialReferenceDTO> list(String projectId, String environmentId) {
        String resolved = projectService.resolveProjectId(projectId);
        String sql = "SELECT * FROM ai_credential_reference WHERE project_id=?" +
                (StringUtils.isBlank(environmentId) ? "" : " AND environment_id=?") + " ORDER BY update_time DESC";
        Object[] args = StringUtils.isBlank(environmentId) ? new Object[]{resolved} : new Object[]{resolved, environmentId};
        return jdbcTemplate.query(sql, (rs, row) -> map(rs), args);
    }

    public List<AgentCredentialReferenceDTO> listByEnvironmentProfile(String projectId, String environmentProfileId,
                                                                       String businessRole) {
        String resolvedProjectId = projectService.resolveProjectId(projectId);
        Map<String, Object> profile = jdbcTemplate.queryForMap(
                "SELECT project_id,environment_id FROM ai_environment_execution_profile WHERE id=? AND project_id=?",
                environmentProfileId, resolvedProjectId);
        String environmentId = String.valueOf(profile.get("environment_id"));
        String sql = "SELECT * FROM ai_credential_reference WHERE project_id=? AND environment_id=?" +
                (StringUtils.isBlank(businessRole) ? "" : " AND business_role=?") + " ORDER BY update_time DESC";
        Object[] args = StringUtils.isBlank(businessRole)
                ? new Object[]{resolvedProjectId, environmentId}
                : new Object[]{resolvedProjectId, environmentId, businessRole};
        return jdbcTemplate.query(sql, (rs, row) -> map(rs), args);
    }

    public AgentCredentialReferenceDTO getMetadata(String id) {
        List<AgentCredentialReferenceDTO> rows = jdbcTemplate.query("SELECT * FROM ai_credential_reference WHERE id=?",
                (rs, row) -> map(rs), id);
        if (rows.isEmpty()) throw new MSException("凭据引用不存在");
        AgentCredentialReferenceDTO dto = rows.getFirst();
        projectService.resolveProjectId(dto.getProjectId());
        return dto;
    }

    public AgentCredentialReferenceDTO assertUsable(String id, String projectId, String environmentId, String businessRole) {
        AgentCredentialReferenceDTO metadata = getMetadata(id);
        if (!StringUtils.equals(projectId, metadata.getProjectId())) throw new MSException("CREDENTIAL_REFERENCE_PROJECT_MISMATCH");
        if (StringUtils.isNotBlank(environmentId) && !StringUtils.equals(environmentId, metadata.getEnvironmentId())) throw new MSException("CREDENTIAL_REFERENCE_ENVIRONMENT_MISMATCH");
        if (StringUtils.isNotBlank(businessRole) && !StringUtils.equals(businessRole, metadata.getBusinessRole())) throw new MSException("CREDENTIAL_REFERENCE_ROLE_MISMATCH");
        if (!Boolean.TRUE.equals(metadata.getEnabled())) throw new MSException("CREDENTIAL_REFERENCE_DISABLED");
        if (!"ACTIVE".equals(metadata.getStatus()) || !"PASSED".equals(metadata.getLastVerifyStatus())) throw new MSException("CREDENTIAL_REFERENCE_NOT_VERIFIED");
        if (metadata.getExpiresAt() != null && metadata.getExpiresAt() <= System.currentTimeMillis()) throw new MSException("CREDENTIAL_REFERENCE_EXPIRED");
        return metadata;
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentCredentialReferenceDTO create(AgentCredentialReferenceRequest request) {
        String projectId = projectService.resolveProjectId(request.getProjectId());
        Project project = projectMapper.selectByPrimaryKey(projectId);
        if (project == null) throw new MSException("项目不存在");
        validate(request);
        String id = IDGenerator.nextStr(); long now = System.currentTimeMillis(); String user = SessionUtils.getUserId();
        try {
            jdbcTemplate.update("""
              INSERT INTO ai_credential_reference
              (id,organization_id,project_id,environment_id,name,credential_type,business_role,provider_type,secret_ref,
               username_hint,status,expires_at,enabled,version,create_user,update_user,create_time,update_time)
              VALUES (?,?,?,?,?,?,?,?,?,?,'UNVERIFIED',?,?,0,?,?,?,?)
              """, id, project.getOrganizationId(), projectId, request.getEnvironmentId(), request.getName().trim(),
                    request.getCredentialType().toUpperCase(Locale.ROOT), request.getBusinessRole().trim(),
                    request.getProviderType().toUpperCase(Locale.ROOT), request.getSecretRef().trim(), request.getUsernameHint(),
                    request.getExpiresAt(), request.getEnabled(), user, user, now, now);
        } catch (DuplicateKeyException ex) { throw new MSException("同一环境下凭据引用名称不能重复"); }
        auditLog.audit("AI_CREDENTIAL_REFERENCE_CREATED", id, "credential metadata created");
        return getMetadata(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentCredentialReferenceDTO update(String id, AgentCredentialReferenceRequest request) {
        AgentCredentialReferenceDTO existing = getMetadata(id);
        if (!existing.getProjectId().equals(projectService.resolveProjectId(request.getProjectId()))) throw new MSException("凭据引用不允许跨项目迁移");
        validate(request);
        int version = request.getVersion() == null ? existing.getVersion() : request.getVersion();
        int changed = jdbcTemplate.update("""
          UPDATE ai_credential_reference SET environment_id=?,name=?,credential_type=?,business_role=?,provider_type=?,
          secret_ref=?,username_hint=?,status='UNVERIFIED',expires_at=?,last_verified_at=NULL,last_verify_status=NULL,
          last_verify_message=NULL,enabled=?,version=version+1,update_user=?,update_time=? WHERE id=? AND project_id=? AND version=?
          """, request.getEnvironmentId(), request.getName().trim(), request.getCredentialType().toUpperCase(Locale.ROOT),
                request.getBusinessRole().trim(), request.getProviderType().toUpperCase(Locale.ROOT), request.getSecretRef().trim(),
                request.getUsernameHint(), request.getExpiresAt(), request.getEnabled(), SessionUtils.getUserId(),
                System.currentTimeMillis(), id, existing.getProjectId(), version);
        if (changed != 1) throw new MSException("凭据引用已被修改，请刷新后重试");
        auditLog.audit("AI_CREDENTIAL_REFERENCE_UPDATED", id, "credential reference rotated");
        return getMetadata(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentCredentialVerifyResult verify(String id) {
        AgentCredentialReferenceDTO metadata = getMetadata(id);
        if (!metadata.getEnabled()) throw new MSException("凭据引用已停用");
        if (metadata.getExpiresAt() != null && metadata.getExpiresAt() <= System.currentTimeMillis()) {
            throw new MSException("凭据引用已过期");
        }
        int expectedVersion = metadata.getVersion();
        String secretRef = jdbcTemplate.queryForObject("SELECT secret_ref FROM ai_credential_reference WHERE id=?", String.class, id);
        String traceId = UUID.randomUUID().toString(); long now = System.currentTimeMillis();
        try {
            AgentSecretProvider.SecretMetadata result = providers.require(metadata.getProviderType()).verify(secretRef);
            int changed = jdbcTemplate.update("UPDATE ai_credential_reference SET status='ACTIVE',secret_version=?,last_verified_at=?,last_verify_status='PASSED',last_verify_message='验证通过',version=version+1,update_time=? WHERE id=? AND version=? AND enabled=b'1'",
                    result.version(), now, now, id, expectedVersion);
            if (changed != 1) throw new MSException("凭据引用已被修改，请重新验证");
            auditLog.audit("AI_CREDENTIAL_REFERENCE_VERIFIED", id, "credential reference verified; traceId=" + traceId);
            return new AgentCredentialVerifyResult(true, "PASSED", "验证通过", traceId);
        } catch (RuntimeException ex) {
            if (ex instanceof MSException && StringUtils.contains(ex.getMessage(), "已被修改")) {
                throw ex;
            }
            jdbcTemplate.update("UPDATE ai_credential_reference SET status='UNAVAILABLE',last_verified_at=?,last_verify_status='FAILED',last_verify_message='密钥引用不可用',version=version+1,update_time=? WHERE id=? AND version=?", now, now, id, expectedVersion);
            auditLog.audit("AI_CREDENTIAL_REFERENCE_VERIFY_FAILED", id, "credential verification failed; traceId=" + traceId);
            return new AgentCredentialVerifyResult(false, "FAILED", "密钥引用不可用", traceId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentCredentialReferenceDTO setEnabled(String id, boolean enabled) {
        AgentCredentialReferenceDTO existing = getMetadata(id);
        int changed = jdbcTemplate.update("""
                UPDATE ai_credential_reference
                   SET enabled=?, status=?, version=version+1, update_user=?, update_time=?
                 WHERE id=? AND project_id=? AND version=?
                """, enabled, enabled ? "UNVERIFIED" : "DISABLED", SessionUtils.getUserId(),
                System.currentTimeMillis(), id, existing.getProjectId(), existing.getVersion());
        if (changed != 1) {
            throw new MSException("凭据引用已被修改，请刷新后重试");
        }
        auditLog.audit(enabled ? "AI_CREDENTIAL_REFERENCE_ENABLED" : "AI_CREDENTIAL_REFERENCE_DISABLED", id,
                enabled ? "credential enabled" : "credential disabled");
        return getMetadata(id);
    }

    public AgentCredentialResolveResponse resolveForRunner(String authorization, String taskId, String referenceId,
                                                            AgentCredentialResolveRequest request) {
        AgentRunnerLeaseDTO lease = runnerService.requireActiveLease(authorization, request.getLeaseId());
        if (!taskId.equals(lease.getTaskId()) || !AgentExecutorChannel.MODEL_API_RUNNER.equals(lease.getExecutorChannel())) {
            throw new MSException("RUNNER_LEASE_TASK_MISMATCH");
        }
        Map<String, Object> binding = jdbcTemplate.queryForMap("""
            SELECT t.project_id,t.environment_profile_id,t.credential_reference_id,p.environment_id
              FROM ai_execution_task t
              JOIN ai_environment_execution_profile p ON p.id=t.environment_profile_id AND p.project_id=t.project_id
             WHERE t.id=?
            """, taskId);
        if (!referenceId.equals(binding.get("credential_reference_id"))) throw new MSException("CREDENTIAL_REFERENCE_TASK_MISMATCH");
        AgentCredentialReferenceDTO metadata = getMetadata(referenceId);
        if (!metadata.getEnabled() || !"ACTIVE".equals(metadata.getStatus())) throw new MSException("CREDENTIAL_REFERENCE_NOT_ACTIVE");
        if (metadata.getExpiresAt() != null && metadata.getExpiresAt() <= System.currentTimeMillis()) throw new MSException("CREDENTIAL_REFERENCE_EXPIRED");
        if (!metadata.getProjectId().equals(binding.get("project_id")) || !metadata.getEnvironmentId().equals(binding.get("environment_id"))) {
            throw new MSException("CREDENTIAL_REFERENCE_ENVIRONMENT_MISMATCH");
        }
        String secretRef = jdbcTemplate.queryForObject("SELECT secret_ref FROM ai_credential_reference WHERE id=? AND version=?", String.class,
                referenceId, metadata.getVersion());
        String traceId = UUID.randomUUID().toString();
        SecretResolveContext context = new SecretResolveContext(taskId, lease.getExecutionId(), metadata.getProjectId(),
                metadata.getEnvironmentId(), referenceId, request.getPurpose(), traceId);
        AgentSecretProvider provider = providers.require(metadata.getProviderType());
        try (ResolvedSecret secret = provider.resolve(secretRef, metadata.getUsernameHint(), context)) {
            String payload = JSON.toJSONString(Map.of("username", StringUtils.defaultString(secret.username()),
                    "value", new String(secret.valueCopy())));
            EncryptedSecret encrypted = encrypt(payload.getBytes(StandardCharsets.UTF_8), request.getRunnerPublicKey());
            auditLog.audit("AI_CREDENTIAL_RUNTIME_RESOLVED", referenceId, "taskId=" + taskId + ";traceId=" + traceId);
            return new AgentCredentialResolveResponse("RSA-OAEP-256+A256GCM", encrypted.encryptedKey(), encrypted.iv(),
                    encrypted.payload(), secret.version(), secret.expiresAt(), traceId);
        } finally {
            provider.revokeLease(context);
        }
    }

    private EncryptedSecret encrypt(byte[] payload, String pemPublicKey) {
        try {
            String normalized = pemPublicKey.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(normalized)));
            KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(256);
            SecretKey dataKey = generator.generateKey();
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(128, iv));
            byte[] ciphertext = aes.doFinal(payload);
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            rsa.init(Cipher.ENCRYPT_MODE, key, new OAEPParameterSpec("SHA-256", "MGF1",
                    MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
            byte[] encryptedKey = rsa.doFinal(dataKey.getEncoded());
            return new EncryptedSecret(Base64.getEncoder().encodeToString(encryptedKey),
                    Base64.getEncoder().encodeToString(iv), Base64.getEncoder().encodeToString(ciphertext));
        } catch (Exception ex) {
            throw new MSException("RUNNER_PUBLIC_KEY_INVALID");
        }
    }

    private record EncryptedSecret(String encryptedKey, String iv, String payload) { }

    private void validate(AgentCredentialReferenceRequest request) {
        if (!TYPES.contains(request.getCredentialType().toUpperCase(Locale.ROOT))) throw new MSException("不支持的凭据类型");
        if (request.getExpiresAt() != null && request.getExpiresAt() <= System.currentTimeMillis()) throw new MSException("凭据过期时间必须晚于当前时间");
        providers.require(request.getProviderType()).validateReference(request.getSecretRef());
        Integer environmentCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ai_environment_execution_profile WHERE project_id=? AND environment_id=?",
                Integer.class, projectService.resolveProjectId(request.getProjectId()), request.getEnvironmentId());
        if (environmentCount == null || environmentCount == 0) throw new MSException("凭据引用对应的环境执行配置不存在");
    }

    private AgentCredentialReferenceDTO map(java.sql.ResultSet rs) throws java.sql.SQLException {
        AgentCredentialReferenceDTO d = new AgentCredentialReferenceDTO();
        d.setId(rs.getString("id")); d.setProjectId(rs.getString("project_id")); d.setEnvironmentId(rs.getString("environment_id"));
        d.setName(rs.getString("name")); d.setCredentialType(rs.getString("credential_type")); d.setBusinessRole(rs.getString("business_role"));
        d.setProviderType(rs.getString("provider_type")); d.setSecretVersion(rs.getString("secret_version")); d.setUsernameHint(rs.getString("username_hint"));
        d.setStatus(rs.getString("status")); d.setExpiresAt(nullableLong(rs,"expires_at")); d.setLastVerifiedAt(nullableLong(rs,"last_verified_at"));
        d.setLastVerifyStatus(rs.getString("last_verify_status")); d.setLastVerifyMessage(rs.getString("last_verify_message"));
        d.setEnabled(rs.getBoolean("enabled")); d.setVersion(rs.getInt("version")); d.setCreateTime(rs.getLong("create_time")); d.setUpdateTime(rs.getLong("update_time"));
        return d;
    }
    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException { long value=rs.getLong(column); return rs.wasNull()?null:value; }
}
