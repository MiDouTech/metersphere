package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentEnvironmentProfileDTO;
import io.metersphere.agent.dto.AgentEnvironmentProfileRequest;
import io.metersphere.agent.dto.AgentEnvironmentVerifyResult;
import io.metersphere.agent.dto.AgentEnvironmentVerifyRequest;
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

import java.net.InetAddress;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AgentEnvironmentProfileService {
    private static final Set<String> TYPES = Set.of("TEST", "STAGING", "PRODUCTION");
    private static final Set<String> RUNNER_TYPES = Set.of("BROWSER", "API");

    @Resource private JdbcTemplate jdbcTemplate;
    @Resource(name = "agentProjectService") private AgentProjectService projectService;
    @Resource private ProjectMapper projectMapper;

    public List<AgentEnvironmentProfileDTO> list(String projectId) {
        String resolved = projectService.resolveProjectId(projectId);
        return jdbcTemplate.query("SELECT * FROM ai_environment_execution_profile WHERE project_id=? ORDER BY update_time DESC",
                (rs, row) -> map(rs), resolved);
    }

    public AgentEnvironmentProfileDTO get(String id) {
        List<AgentEnvironmentProfileDTO> rows = jdbcTemplate.query(
                "SELECT * FROM ai_environment_execution_profile WHERE id=?", (rs, row) -> map(rs), id);
        if (rows.isEmpty()) throw new MSException("环境执行配置不存在");
        AgentEnvironmentProfileDTO dto = rows.getFirst();
        projectService.resolveProjectId(dto.getProjectId());
        return dto;
    }

    public AgentEnvironmentProfileDTO get(String projectId, String id) {
        String resolvedProjectId = projectService.resolveProjectId(projectId);
        AgentEnvironmentProfileDTO profile = get(id);
        if (!resolvedProjectId.equals(profile.getProjectId())) {
            throw new MSException("ENVIRONMENT_PROFILE_PROJECT_MISMATCH");
        }
        return profile;
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentEnvironmentProfileDTO create(AgentEnvironmentProfileRequest request) {
        String projectId = projectService.resolveProjectId(request.getProjectId());
        Project project = projectMapper.selectByPrimaryKey(projectId);
        if (project == null) throw new MSException("项目不存在");
        validate(request);
        if (StringUtils.isNotBlank(request.getLoginProfileId()) || StringUtils.isNotBlank(request.getDefaultCredentialReferenceId())) {
            throw new MSException("ENVIRONMENT_BINDINGS_REQUIRE_PROFILE_UPDATE_AFTER_CREATE");
        }
        String id = IDGenerator.nextStr();
        long now = System.currentTimeMillis();
        String user = SessionUtils.getUserId();
        try {
            jdbcTemplate.update("""
                INSERT INTO ai_environment_execution_profile
                (id,organization_id,project_id,environment_id,name,base_url,allowed_origins,network_zone,
                 environment_type,login_profile_id,default_credential_reference_id,runner_type,required_capabilities,
                 production_allowed,enabled,version,create_user,update_user,create_time,update_time)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,b'0',?,0,?,?,?,?)
                """, id, project.getOrganizationId(), projectId, request.getEnvironmentId(), request.getName().trim(),
                    normalizeUrl(request.getBaseUrl()), JSON.toJSONString(normalizeOrigins(request.getAllowedOrigins())),
                    request.getNetworkZone(), request.getEnvironmentType().toUpperCase(Locale.ROOT), request.getLoginProfileId(),
                    request.getDefaultCredentialReferenceId(), request.getRunnerType().toUpperCase(Locale.ROOT),
                    JSON.toJSONString(nullSafe(request.getRequiredCapabilities())), request.getEnabled() == null || request.getEnabled(), user, user, now, now);
        } catch (DuplicateKeyException ex) {
            throw new MSException("同一项目下环境执行配置名称不能重复");
        }
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentEnvironmentProfileDTO update(String id, AgentEnvironmentProfileRequest request) {
        AgentEnvironmentProfileDTO existing = get(id);
        if (!StringUtils.equals(existing.getProjectId(), projectService.resolveProjectId(request.getProjectId()))) {
            throw new MSException("环境执行配置不允许跨项目迁移");
        }
        validate(request);
        validateBindings(id, existing.getProjectId(), request);
        int expectedVersion = request.getVersion() == null ? existing.getVersion() : request.getVersion();
        int changed = jdbcTemplate.update("""
            UPDATE ai_environment_execution_profile
               SET environment_id=?,name=?,base_url=?,allowed_origins=?,network_zone=?,environment_type=?,
                   login_profile_id=?,default_credential_reference_id=?,runner_type=?,required_capabilities=?,
                   production_allowed=b'0',enabled=?,version=version+1,update_user=?,update_time=?
             WHERE id=? AND project_id=? AND version=?
            """, request.getEnvironmentId(), request.getName().trim(), normalizeUrl(request.getBaseUrl()),
                JSON.toJSONString(normalizeOrigins(request.getAllowedOrigins())), request.getNetworkZone(),
                request.getEnvironmentType().toUpperCase(Locale.ROOT), request.getLoginProfileId(),
                request.getDefaultCredentialReferenceId(), request.getRunnerType().toUpperCase(Locale.ROOT),
                JSON.toJSONString(nullSafe(request.getRequiredCapabilities())), request.getEnabled(), SessionUtils.getUserId(),
                System.currentTimeMillis(), id, existing.getProjectId(), expectedVersion);
        if (changed != 1) throw new MSException("环境执行配置已被修改，请刷新后重试");
        return get(id);
    }

    public AgentEnvironmentVerifyResult verify(String id, AgentEnvironmentVerifyRequest request) {
        AgentEnvironmentProfileDTO profile = get(id);
        AgentEnvironmentVerifyResult result = new AgentEnvironmentVerifyResult();
        result.setTraceId(UUID.randomUUID().toString());
        URI uri = requireSafeHttpUri(StringUtils.defaultIfBlank(request.getTargetUrl(), profile.getBaseUrl()));
        boolean allowed = profile.getAllowedOrigins().contains(origin(uri));
        result.setOriginAllowed(allowed);
        result.getChecks().add(allowed ? "TARGET_ORIGIN_ALLOWED" : "TARGET_ORIGIN_BLOCKED");
        if (!allowed) {
            result.setValid(false);
            return result;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            result.setDnsResolved(addresses.length > 0);
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()) {
                    throw new MSException("目标域名解析到禁止访问的地址");
                }
            }
            result.getChecks().add("DNS_RESOLVED_PUBLIC");
            int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket()) {
                    socket.connect(new InetSocketAddress(uri.getHost(), port), 3000);
                    socket.setSoTimeout(3000);
                    socket.startHandshake();
                    result.setTlsValid(true);
                    result.setReachable(true);
                }
                result.getChecks().add("TLS_HANDSHAKE_PASSED");
            } else {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(uri.getHost(), port), 3000);
                    result.setReachable(true);
                }
                result.setTlsValid(true);
                result.getChecks().add("TCP_CONNECT_PASSED");
            }
        } catch (MSException ex) {
            throw ex;
        } catch (Exception ex) {
            result.getChecks().add("TARGET_UNREACHABLE");
        }
        result.setRunnerMatched(checkRunner(profile, request.getRunnerId()));
        result.getChecks().add(result.isRunnerMatched() ? "RUNNER_MATCHED" : "RUNNER_NOT_MATCHED");
        result.setValid(result.isOriginAllowed() && result.isDnsResolved() && result.isTlsValid()
                && result.isReachable() && result.isRunnerMatched());
        return result;
    }

    private boolean checkRunner(AgentEnvironmentProfileDTO profile, String runnerId) {
        if (StringUtils.isBlank(runnerId)) return true;
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ai_runner WHERE id=? AND organization_id=? AND status='ONLINE'",
                Integer.class, runnerId, profile.getOrganizationId());
        return count != null && count == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentEnvironmentProfileDTO setEnabled(String id, boolean enabled) {
        AgentEnvironmentProfileDTO existing = get(id);
        int changed = jdbcTemplate.update("UPDATE ai_environment_execution_profile SET enabled=?,version=version+1,update_user=?,update_time=? WHERE id=? AND project_id=? AND version=?",
                enabled, SessionUtils.getUserId(), System.currentTimeMillis(), id, existing.getProjectId(), existing.getVersion());
        if (changed != 1) throw new MSException("环境执行配置已被修改，请刷新后重试");
        return get(id);
    }

    public void assertTargetAllowed(AgentEnvironmentProfileDTO profile, String targetUrl) {
        URI target = requireSafeHttpUri(targetUrl);
        if (!profile.getAllowedOrigins().contains(origin(target))) {
            throw new MSException("目标地址不在环境允许范围内");
        }
    }

    public AgentEnvironmentProfileDTO resolveForTask(String id, String projectId) {
        AgentEnvironmentProfileDTO profile = get(id);
        if (!StringUtils.equals(profile.getProjectId(), projectId)) {
            throw new MSException("ENVIRONMENT_PROFILE_PROJECT_MISMATCH");
        }
        if (!Boolean.TRUE.equals(profile.getEnabled())) {
            throw new MSException("ENVIRONMENT_PROFILE_DISABLED");
        }
        if ("PRODUCTION".equals(profile.getEnvironmentType()) || Boolean.TRUE.equals(profile.getProductionAllowed())) {
            throw new MSException("PRODUCTION_EXECUTION_FORBIDDEN");
        }
        return profile;
    }

    public String freezeSnapshot(AgentEnvironmentProfileDTO profile) {
        return JSON.toJSONString(java.util.Map.of(
                "id", profile.getId(), "environmentId", profile.getEnvironmentId(), "version", profile.getVersion(),
                "baseUrl", profile.getBaseUrl(), "allowedOrigins", profile.getAllowedOrigins(),
                "networkZone", StringUtils.defaultString(profile.getNetworkZone()), "environmentType", profile.getEnvironmentType(),
                "runnerType", profile.getRunnerType(), "requiredCapabilities", profile.getRequiredCapabilities(),
                "loginProfileId", StringUtils.defaultString(profile.getLoginProfileId())));
    }

    private void validate(AgentEnvironmentProfileRequest request) {
        String type = request.getEnvironmentType().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new MSException("不支持的环境类型");
        if ("PRODUCTION".equals(type)) throw new MSException("首期不允许配置生产环境执行");
        if (!RUNNER_TYPES.contains(request.getRunnerType().toUpperCase(Locale.ROOT))) throw new MSException("不支持的 Runner 类型");
        URI base = requireSafeHttpUri(request.getBaseUrl());
        List<String> origins = normalizeOrigins(request.getAllowedOrigins());
        if (!origins.contains(origin(base))) throw new MSException("基础地址必须属于 allowedOrigins");
    }

    private void validateBindings(String profileId,String projectId,AgentEnvironmentProfileRequest request){
        if(StringUtils.isNotBlank(request.getLoginProfileId())){Integer count=jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ai_login_profile WHERE id=? AND project_id=? AND environment_profile_id=? AND enabled=1",Integer.class,request.getLoginProfileId(),projectId,profileId);if(count==null||count!=1)throw new MSException("LOGIN_PROFILE_BINDING_INVALID");}
        if(StringUtils.isNotBlank(request.getDefaultCredentialReferenceId())){Integer count=jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ai_credential_reference WHERE id=? AND project_id=? AND environment_id=? AND enabled=1",Integer.class,request.getDefaultCredentialReferenceId(),projectId,request.getEnvironmentId());if(count==null||count!=1)throw new MSException("CREDENTIAL_REFERENCE_BINDING_INVALID");}
    }

    private URI requireSafeHttpUri(String value) {
        try {
            URI uri = URI.create(value).normalize();
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw new MSException("只允许完整的 HTTP/HTTPS 地址");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.equals("169.254.169.254")) throw new MSException("禁止访问本机或云元数据地址");
            // Do not make profile validation depend on DNS availability. Runner must resolve again
            // immediately before connecting and reject private/loopback/link-local resolved addresses.
            if (isIpLiteral(host)) {
                InetAddress address = InetAddress.getByName(host);
                if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                    throw new MSException("禁止访问本机、私网或链路本地地址");
                }
            }
            return uri;
        } catch (MSException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MSException("环境地址格式无效或无法解析");
        }
    }

    private boolean isIpLiteral(String host) {
        return host.matches("^[0-9.]+$") || host.contains(":");
    }

    private String normalizeUrl(String value) {
        URI uri = requireSafeHttpUri(value);
        return uri.toString();
    }

    private List<String> normalizeOrigins(List<String> values) {
        return values.stream().map(this::requireSafeHttpUri).map(this::origin).distinct().toList();
    }

    private String origin(URI uri) {
        int port = uri.getPort();
        boolean defaultPort = port < 0 || (port == 80 && "http".equalsIgnoreCase(uri.getScheme()))
                || (port == 443 && "https".equalsIgnoreCase(uri.getScheme()));
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                + (defaultPort ? "" : ":" + port);
    }

    private List<String> nullSafe(List<String> values) { return values == null ? List.of() : values; }

    private AgentEnvironmentProfileDTO map(java.sql.ResultSet rs) throws java.sql.SQLException {
        AgentEnvironmentProfileDTO dto = new AgentEnvironmentProfileDTO();
        dto.setId(rs.getString("id")); dto.setOrganizationId(rs.getString("organization_id"));
        dto.setProjectId(rs.getString("project_id")); dto.setEnvironmentId(rs.getString("environment_id"));
        dto.setName(rs.getString("name")); dto.setBaseUrl(rs.getString("base_url"));
        dto.setAllowedOrigins(jsonList(rs.getString("allowed_origins"))); dto.setNetworkZone(rs.getString("network_zone"));
        dto.setEnvironmentType(rs.getString("environment_type")); dto.setLoginProfileId(rs.getString("login_profile_id"));
        dto.setDefaultCredentialReferenceId(rs.getString("default_credential_reference_id")); dto.setRunnerType(rs.getString("runner_type"));
        dto.setRequiredCapabilities(jsonList(rs.getString("required_capabilities"))); dto.setProductionAllowed(rs.getBoolean("production_allowed"));
        dto.setEnabled(rs.getBoolean("enabled")); dto.setVersion(rs.getInt("version")); dto.setCreateUser(rs.getString("create_user"));
        dto.setUpdateUser(rs.getString("update_user")); dto.setCreateTime(rs.getLong("create_time")); dto.setUpdateTime(rs.getLong("update_time"));
        return dto;
    }

    private List<String> jsonList(String value) {
        if (StringUtils.isBlank(value)) return List.of();
        return Arrays.asList(JSON.parseArray(value, String.class).toArray(String[]::new));
    }
}
