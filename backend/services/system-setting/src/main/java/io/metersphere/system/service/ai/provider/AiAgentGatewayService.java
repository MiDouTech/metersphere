package io.metersphere.system.service.ai.provider;

import io.metersphere.system.dto.request.ai.AiAgentGatewayCapabilityDTO;
import io.metersphere.system.dto.request.ai.AiAgentGatewayRequest;
import io.metersphere.system.dto.request.ai.AiAgentGatewayInvokeRequest;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.EncryptUtils;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.service.ai.AiAuditService;
import io.metersphere.system.service.PermissionCheckService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.constants.UserRoleType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Service
public class AiAgentGatewayService {
    private static final Set<String> KNOWN_PRODUCT_NAMES = Set.of("cursor", "codex", "workbuddy");
    private static final Set<String> PROTOCOLS = Set.of("MCP", "CUSTOM_HTTP");
    private static final Set<String> AUTH_TYPES = Set.of("NONE", "BEARER", "API_KEY");
    private static final String MCP_PROTOCOL_VERSION = "2026-07-28";
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private AiAuditService aiAuditService;
    @Resource private PermissionCheckService permissionCheckService;
    @Value("${metersphere.ai.agent-gateway.allow-private-addresses:false}")
    private boolean allowPrivateAddresses;
    private final RestClient restClient;

    public AiAgentGatewayService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public AiAgentGatewayCapabilityDTO save(AiAgentGatewayRequest request, String userId) {
        if (!PROTOCOLS.contains(StringUtils.upperCase(request.getProtocol()))) {
            throw new MSException("Agent Gateway protocol 仅支持 MCP/CUSTOM_HTTP");
        }
        if (!AUTH_TYPES.contains(StringUtils.upperCase(request.getAuthType()))) {
            throw new MSException("Agent Gateway authType 仅支持 NONE/BEARER/API_KEY");
        }
        validateUrl(request.getBaseUrl());
        String agentType = normalizeAgentType(request.getAgentType());
        assertScopeAccess(request.getProjectId(), request.getOrganizationId(), userId);
        if (StringUtils.isNotBlank(request.getId())) {
            requireGateway(request.getId(), userId, PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG);
        }
        String id = StringUtils.defaultIfBlank(request.getId(), IDGenerator.nextStr());
        long now = System.currentTimeMillis();
        jdbcTemplate.update("""
                INSERT INTO ai_agent_gateway
                (id,name,agent_type,protocol,base_url,auth_type,auth_cipher,organization_id,project_id,owner_user_id,
                 enabled,capabilities,create_user,create_time,update_user,update_time)
                VALUES (?,?,?,?,?,?,?,?,?,?,1,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE name=VALUES(name), agent_type=VALUES(agent_type), protocol=VALUES(protocol), base_url=VALUES(base_url),
                  auth_type=VALUES(auth_type), auth_cipher=COALESCE(VALUES(auth_cipher),auth_cipher),
                  organization_id=VALUES(organization_id), project_id=VALUES(project_id), owner_user_id=VALUES(owner_user_id),
                  enabled=1, capabilities=VALUES(capabilities), update_user=VALUES(update_user), update_time=VALUES(update_time)
                """, id, request.getName(), agentType, StringUtils.upperCase(request.getProtocol()), request.getBaseUrl(),
                StringUtils.upperCase(request.getAuthType()), StringUtils.isBlank(request.getAuthToken()) ? null : EncryptUtils.aesEncrypt(request.getAuthToken()),
                request.getOrganizationId(), request.getProjectId(), request.isPersonal() ? userId : null,
                JSON.toJSONString(request.getCapabilities()), userId, now, userId, now);
        aiAuditService.record(request.getProjectId(), request.getOrganizationId(), userId, id, "UPDATE",
                "AI_AGENT_GATEWAY_CONFIGURE", "/ai/agent-gateway", "POST",
                Map.of("protocol", request.getProtocol(), "agentType", StringUtils.defaultString(agentType),
                        "capabilities", request.getCapabilities()));
        return capability(id, userId);
    }

    public AiAgentGatewayCapabilityDTO capability(String gatewayId, String userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ai_agent_gateway WHERE id=? AND enabled=1", gatewayId);
        if (!rows.isEmpty() && !canAccess(rows.get(0), userId, PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG)) {
            rows = List.of();
        }
        AiAgentGatewayCapabilityDTO dto = new AiAgentGatewayCapabilityDTO();
        dto.setGatewayId(gatewayId);
        dto.setName(StringUtils.defaultIfBlank(gatewayId, "default"));
        dto.setProtocol("MCP_OR_CUSTOM_GATEWAY");
        dto.setConfigured(!rows.isEmpty());
        dto.setOauthSupported(false);
        dto.setQuotaSupported(false);
        dto.setFeatures(List.of());
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            dto.setName((String) row.get("name"));
            dto.setProtocol((String) row.get("protocol"));
            dto.setQuotaSupported(true);
            dto.setFeatures(JSON.parseArray(StringUtils.defaultString((String) row.get("capabilities"), "[]"), String.class));
            dto.setMessage("企业 Agent 网关已配置");
        } else if (KNOWN_PRODUCT_NAMES.contains(StringUtils.lowerCase(gatewayId))) {
            dto.setMessage("该产品名称不能直接视为开放模型 API；需接入官方开放能力或企业 Agent 网关后才可启用。");
        } else {
            dto.setMessage("企业 Agent 网关尚未配置，当前仅返回能力声明占位。");
        }
        log.info("ai_agent_gateway_capability gatewayId={}, configured={}", gatewayId, dto.isConfigured());
        return dto;
    }

    public Map<?, ?> invoke(AiAgentGatewayInvokeRequest request, String userId) {
        return invoke(request, userId, PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE);
    }

    public List<AiAgentGatewayCapabilityDTO> executionAgentCapabilities(String projectId, String userId) {
        List<AiAgentGatewayCapabilityDTO> result = new ArrayList<>();
        for (String product : List.of("workbuddy", "cursor", "codex")) {
            Map<String, Object> gateway = findExecutionGateway(product, projectId, userId);
            AiAgentGatewayCapabilityDTO dto = new AiAgentGatewayCapabilityDTO();
            dto.setGatewayId(gateway == null ? null : (String) gateway.get("id"));
            dto.setName(product.substring(0, 1).toUpperCase(Locale.ROOT) + product.substring(1));
            dto.setProtocol(gateway == null ? null : (String) gateway.get("protocol"));
            dto.setConfigured(gateway != null);
            dto.setFeatures(gateway == null ? List.of() : JSON.parseArray(
                    StringUtils.defaultString((String) gateway.get("capabilities"), "[]"), String.class));
            dto.setMessage(gateway == null ? "Agent Gateway 未配置" : "Agent Gateway 已就绪");
            result.add(dto);
        }
        return result;
    }

    public String requireExecutionAgentGateway(String agentType, String projectId, String userId) {
        String normalized = normalizeAgentType(agentType);
        Map<String, Object> gateway = findExecutionGateway(normalized, projectId, userId);
        if (gateway == null) {
            throw new MSException(normalized + " Agent Gateway 未配置、未启用或当前用户无权访问");
        }
        return (String) gateway.get("id");
    }

    public Map<?, ?> invokeExecutionAgent(String gatewayId, String projectId, String taskId,
                                           Map<String, Object> context, String userId) {
        AiAgentGatewayInvokeRequest request = new AiAgentGatewayInvokeRequest();
        request.setGatewayId(gatewayId);
        request.setProjectId(projectId);
        request.setTaskId(taskId);
        request.setOperation("metersphere.webui.execute");
        request.setContext(context);
        return invoke(request, userId, PermissionConstants.AI_EXECUTION_RUN);
    }

    private Map<?, ?> invoke(AiAgentGatewayInvokeRequest request, String userId, String permission) {
        Map<String, Object> gateway = requireGateway(request.getGatewayId(), userId, permission);
        if (StringUtils.isNotBlank((String) gateway.get("project_id"))
                && !StringUtils.equals(request.getProjectId(), (String) gateway.get("project_id"))) {
            throw new MSException("Agent Gateway 与请求项目不匹配");
        }
        List<String> capabilities = JSON.parseArray(
                StringUtils.defaultString((String) gateway.get("capabilities"), "[]"), String.class);
        if (!capabilities.isEmpty() && !capabilities.contains(request.getOperation())) {
            throw new MSException("Agent Gateway 未声明该操作能力");
        }
        String protocol = (String) gateway.get("protocol");
        Map<String, Object> payload = "MCP".equals(protocol)
                ? Map.of("jsonrpc", "2.0", "id", UUID.randomUUID().toString(), "method", "tools/call",
                         "params", Map.of("name", request.getOperation(), "arguments", request.getContext(),
                                 "_meta", Map.of(
                                         "io.modelcontextprotocol/clientInfo", Map.of("name", "metersphere", "version", "3.x"),
                                         "io.metersphere/taskContext", Map.of(
                                                 "taskId", StringUtils.defaultString(request.getTaskId()),
                                                 "projectId", request.getProjectId()))))
                : Map.of("operation", request.getOperation(), "taskId", StringUtils.defaultString(request.getTaskId()),
                         "projectId", request.getProjectId(), "context", request.getContext());
        try {
            RestClient.RequestBodySpec spec = restClient.post().uri((String) gateway.get("base_url"))
                    .contentType(MediaType.APPLICATION_JSON);
            if ("MCP".equals(protocol)) {
                spec.accept(MediaType.APPLICATION_JSON)
                        .header("MCP-Protocol-Version", MCP_PROTOCOL_VERSION)
                        .header("Mcp-Method", "tools/call")
                        .header("Mcp-Name", request.getOperation());
            }
            String cipher = (String) gateway.get("auth_cipher");
            if (StringUtils.isNotBlank(cipher)) {
                String token = EncryptUtils.aesDecrypt(cipher);
                if ("API_KEY".equals(gateway.get("auth_type"))) spec.header("X-API-Key", token);
                else spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            Map<?, ?> response = spec.body(payload).retrieve().body(Map.class);
            if (response == null) throw new MSException("Agent Gateway 返回空响应");
            if (response.get("error") != null) throw mapGatewayError(response.get("error"));
            aiAuditService.record(request.getProjectId(), null, userId, request.getGatewayId(), "EXECUTE",
                    "AI_AGENT_GATEWAY_INVOKE", "/ai/agent-gateway/invoke", "POST",
                    Map.of("operation", request.getOperation(), "taskId", StringUtils.defaultString(request.getTaskId())));
            return response;
        } catch (MSException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MSException("Agent Gateway 调用失败：" + sanitize(ex.getMessage()), ex);
        }
    }

    private Map<String, Object> findExecutionGateway(String agentType, String projectId, String userId) {
        String normalized = normalizeAgentType(agentType);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT * FROM ai_agent_gateway
                WHERE enabled=1 AND (LOWER(agent_type)=? OR (agent_type IS NULL AND LOWER(id)=?))
                ORDER BY CASE
                    WHEN owner_user_id=? THEN 0
                    WHEN project_id=? THEN 1
                    WHEN project_id IS NULL AND organization_id IS NOT NULL THEN 2
                    ELSE 3 END,
                    update_time DESC
                """, normalized.toLowerCase(Locale.ROOT), normalized.toLowerCase(Locale.ROOT), userId, projectId);
        return rows.stream()
                .filter(row -> StringUtils.isBlank((String) row.get("project_id"))
                        || StringUtils.equals(projectId, (String) row.get("project_id")))
                .filter(row -> canAccess(row, userId, PermissionConstants.AI_EXECUTION_RUN))
                .findFirst()
                .orElse(null);
    }

    private String normalizeAgentType(String value) {
        String normalized = StringUtils.upperCase(StringUtils.trimToEmpty(value));
        if (StringUtils.isBlank(normalized)) {
            return null;
        }
        if (!KNOWN_PRODUCT_NAMES.contains(normalized.toLowerCase(Locale.ROOT))) {
            throw new MSException("Agent 类型仅支持 WORKBUDDY/CURSOR/CODEX");
        }
        return normalized;
    }

    public void disable(String id, String userId) {
        Map<String, Object> gateway = requireGateway(id, userId, PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG);
        int affected = jdbcTemplate.update("UPDATE ai_agent_gateway SET enabled=0,update_user=?,update_time=? WHERE id=? AND enabled=1",
                userId, System.currentTimeMillis(), id);
        if (affected == 0) throw new MSException("Agent Gateway 不存在或无权停用");
        aiAuditService.record((String) gateway.get("project_id"), (String) gateway.get("organization_id"),
                userId, id, "DELETE", "AI_AGENT_GATEWAY_DISABLE",
                "/ai/agent-gateway/" + id + "/disable", "POST", Map.of());
    }

    private Map<String, Object> requireGateway(String id, String userId, String permission) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ai_agent_gateway WHERE id=? AND enabled=1", id);
        if (rows.size() != 1 || !canAccess(rows.get(0), userId, permission)) {
            throw new MSException("Agent Gateway 不存在、未启用或无权访问");
        }
        return rows.get(0);
    }

    private boolean canAccess(Map<String, Object> gateway, String userId, String permission) {
        if (StringUtils.isNotBlank((String) gateway.get("owner_user_id"))) {
            return StringUtils.equals(userId, (String) gateway.get("owner_user_id"));
        }
        String projectId = (String) gateway.get("project_id");
        if (StringUtils.isNotBlank(projectId)) {
            return permissionCheckService.userHasSourcePermission(userId, projectId, permission, UserRoleType.PROJECT.name());
        }
        String organizationId = (String) gateway.get("organization_id");
        if (StringUtils.isNotBlank(organizationId)) {
            return permissionCheckService.userHasSourcePermission(userId, organizationId, permission, UserRoleType.ORGANIZATION.name());
        }
        return StringUtils.equals(userId, (String) gateway.get("create_user"));
    }

    private void assertScopeAccess(String projectId, String organizationId, String userId) {
        if (StringUtils.isNotBlank(projectId)
                && !permissionCheckService.userHasSourcePermission(userId, projectId,
                PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG, UserRoleType.PROJECT.name())) {
            throw new MSException("无权配置该项目的 Agent Gateway");
        }
        if (StringUtils.isBlank(projectId) && StringUtils.isNotBlank(organizationId)
                && !permissionCheckService.userHasSourcePermission(userId, organizationId,
                PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG, UserRoleType.ORGANIZATION.name())) {
            throw new MSException("无权配置该组织的 Agent Gateway");
        }
    }

    private void validateUrl(String value) {
        AiRemoteEndpointValidator.validateHttps(value, "Agent Gateway", allowPrivateAddresses);
    }

    private String sanitize(String message) {
        return StringUtils.defaultIfBlank(message, "unknown")
                .replaceAll("(?i)(api[-_ ]?key|token|secret|authorization)\\s*[:=]\\s*[^\\s,;]+", "$1=******");
    }

    private MSException mapGatewayError(Object error) {
        if (error instanceof Map<?, ?> details) {
            Object code = details.get("code");
            String message = sanitize(String.valueOf(details.get("message")));
            String category = switch (String.valueOf(code)) {
                case "-32601" -> "不支持的工具";
                case "-32602" -> "工具参数无效";
                case "-32001" -> "协议头或会话信息不匹配";
                default -> "远程执行失败";
            };
            return new MSException("Agent Gateway " + category + "：" + message);
        }
        return new MSException("Agent Gateway 错误：" + sanitize(JSON.toJSONString(error)));
    }
}
