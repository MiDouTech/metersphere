package io.metersphere.system.service.ai;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.request.ai.AiProjectGovernanceDTO;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class AiGovernanceService {
    private static final List<String> RESOURCE_TYPES = List.of("MODEL_API", "USER_AGENT");
    private static final List<String> AGENT_PROVIDERS = List.of("CODEX", "CURSOR", "WORKBUDDY");
    private static final int DEFAULT_MAX_CONCURRENT = 3;
    private static final long DEFAULT_TOKEN_QUOTA = 1_000_000L;
    private static final long DEFAULT_PROJECT_FILE_QUOTA = 1_073_741_824L;
    private static final int DEFAULT_SESSION_FILE_LIMIT = 20;
    private static final long DEFAULT_SINGLE_FILE_LIMIT = 52_428_800L;

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource private AiAuditService aiAuditService;

    public AiProjectGovernanceDTO get(String projectId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ai_project_governance WHERE project_id = ?", projectId);
        AiProjectGovernanceDTO dto = rows.isEmpty() ? defaults(projectId) : fromRow(rows.get(0));
        dto.setUsedTokens(monthlyTokenUsage(projectId));
        dto.setUsedFileBytes(projectFileBytes(projectId));
        dto.setActiveTasks(activeTasks(projectId));
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public AiProjectGovernanceDTO save(AiProjectGovernanceDTO dto, String userId) {
        List<String> allowedModels = dto.getAllowedModelIds() == null ? List.of()
                : dto.getAllowedModelIds().stream().filter(StringUtils::isNotBlank).distinct().toList();
        dto.setAllowedModelIds(allowedModels);
        dto.setAllowedResourceTypes(normalizeAllowlist(dto.getAllowedResourceTypes(), List.of("MODEL_API")).stream()
                .filter(RESOURCE_TYPES::contains).toList());
        dto.setAllowedAgentProviders(normalizeAllowlist(dto.getAllowedAgentProviders(), List.of()).stream()
                .filter(AGENT_PROVIDERS::contains).toList());
        if (!dto.isAllowPersonalAgent()) {
            dto.setAllowedResourceTypes(dto.getAllowedResourceTypes().stream()
                    .filter(item -> !"USER_AGENT".equals(item)).toList());
            dto.setAllowedAgentProviders(List.of());
            dto.setAllowLocalAgentTools(false);
        } else if (!dto.getAllowedResourceTypes().contains("USER_AGENT")) {
            throw new MSException("允许个人 Agent 时，资源类型必须包含 USER_AGENT");
        }
        if (StringUtils.isNotBlank(dto.getFallbackModelId())
                && CollectionUtils.isNotEmpty(allowedModels)
                && !allowedModels.contains(dto.getFallbackModelId())) {
            throw new MSException("项目默认回退模型必须包含在模型白名单中");
        }
        jdbcTemplate.update("""
                INSERT INTO ai_project_governance
                (project_id, allowed_model_ids, fallback_model_id, max_concurrent_tasks, monthly_token_quota,
                 project_file_quota, session_file_limit, single_file_limit, allowed_resource_types,
                 allowed_agent_providers, allow_personal_agent, allow_local_agent_tools,
                 max_agent_concurrent_tasks, max_agent_execution_minutes, daily_agent_execution_limit,
                 update_user, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE allowed_model_ids=VALUES(allowed_model_ids),
                  fallback_model_id=VALUES(fallback_model_id),
                  max_concurrent_tasks=VALUES(max_concurrent_tasks), monthly_token_quota=VALUES(monthly_token_quota),
                  project_file_quota=VALUES(project_file_quota), session_file_limit=VALUES(session_file_limit),
                  single_file_limit=VALUES(single_file_limit), allowed_resource_types=VALUES(allowed_resource_types),
                  allowed_agent_providers=VALUES(allowed_agent_providers),
                  allow_personal_agent=VALUES(allow_personal_agent),
                  allow_local_agent_tools=VALUES(allow_local_agent_tools),
                  max_agent_concurrent_tasks=VALUES(max_agent_concurrent_tasks),
                  max_agent_execution_minutes=VALUES(max_agent_execution_minutes),
                  daily_agent_execution_limit=VALUES(daily_agent_execution_limit),
                  update_user=VALUES(update_user), update_time=VALUES(update_time)
                """, dto.getProjectId(), JSON.toJSONString(dto.getAllowedModelIds()), dto.getFallbackModelId(), dto.getMaxConcurrentTasks(),
                dto.getMonthlyTokenQuota(), dto.getProjectFileQuota(), dto.getSessionFileLimit(),
                dto.getSingleFileLimit(), JSON.toJSONString(dto.getAllowedResourceTypes()),
                JSON.toJSONString(dto.getAllowedAgentProviders()), dto.isAllowPersonalAgent(),
                dto.isAllowLocalAgentTools(), dto.getMaxAgentConcurrentTasks(),
                dto.getMaxAgentExecutionMinutes(), dto.getDailyAgentExecutionLimit(), userId,
                System.currentTimeMillis());
        aiAuditService.record(dto.getProjectId(), null, userId, dto.getProjectId(), "UPDATE",
                "AI_PROJECT_GOVERNANCE_UPDATE", "/ai/governance", "POST",
                Map.of("allowedModelCount", dto.getAllowedModelIds().size(),
                        "fallbackModelId", StringUtils.defaultString(dto.getFallbackModelId()),
                        "maxConcurrentTasks", dto.getMaxConcurrentTasks(),
                        "monthlyTokenQuota", dto.getMonthlyTokenQuota(),
                        "projectFileQuota", dto.getProjectFileQuota()));
        return get(dto.getProjectId());
    }

    public void assertModelAllowed(String projectId, String modelSourceId) {
        if (StringUtils.isAnyBlank(projectId, modelSourceId)) {
            return;
        }
        List<String> allowed = get(projectId).getAllowedModelIds();
        if (CollectionUtils.isNotEmpty(allowed) && !allowed.contains(modelSourceId)) {
            throw new MSException("当前模型不在项目 AI 模型白名单中");
        }
    }

    public void assertAgentAllowed(String projectId, String provider) {
        AiProjectGovernanceDTO governance = get(projectId);
        if (!governance.isAllowPersonalAgent()
                || !governance.getAllowedResourceTypes().contains("USER_AGENT")) {
            throw new MSException("AI_RESOURCE_NOT_ALLOWED：项目未允许个人 Agent");
        }
        if (CollectionUtils.isNotEmpty(governance.getAllowedAgentProviders())
                && governance.getAllowedAgentProviders().stream()
                .noneMatch(item -> StringUtils.equalsIgnoreCase(item, provider))) {
            throw new MSException("AI_RESOURCE_NOT_ALLOWED：Agent Provider 不在项目白名单中");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void admitAgentExecution(String projectId, String userId, String connectionId,
                                    String provider, Runnable createExecution) {
        lockProject(projectId);
        assertAgentAllowed(projectId, provider);
        AiProjectGovernanceDTO limits = get(projectId);
        Integer concurrent = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM ai_case_execution
                WHERE project_id=? AND resource_type='USER_AGENT'
                  AND status IN ('CREATED','RUNNING','WAITING_CONFIRMATION')
                """, Integer.class, projectId);
        if (concurrent != null && concurrent >= limits.getMaxAgentConcurrentTasks()) {
            throw new MSException("项目 Agent 并发任务已达到上限：" + limits.getMaxAgentConcurrentTasks());
        }
        Long daily = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM ai_agent_usage WHERE project_id=? AND user_id=? AND create_time>=?
                """, Long.class, projectId, userId, dayStart());
        if (daily != null && daily >= limits.getDailyAgentExecutionLimit()) {
            throw new MSException("项目用户当日 Agent 执行次数已达到上限：" + limits.getDailyAgentExecutionLimit());
        }
        createExecution.run();
    }

    public void assertCanStartGeneration(String projectId) {
        AiProjectGovernanceDTO limits = get(projectId);
        if (limits.getActiveTasks() >= limits.getMaxConcurrentTasks()) {
            throw new MSException("项目 AI 并发任务已达到上限：" + limits.getMaxConcurrentTasks());
        }
        if (limits.getUsedTokens() >= limits.getMonthlyTokenQuota()) {
            throw new MSException("项目本月 AI Token 配额已用尽");
        }
    }

    /** Serializes admission per project on the governance row across application nodes. */
    @Transactional(rollbackFor = Exception.class)
    public void admitGeneration(String projectId, Runnable createGeneration) {
        lockProject(projectId);
        assertCanStartGeneration(projectId);
        createGeneration.run();
    }

    /** Keeps the quota check and document insert in the same project-scoped transaction. */
    @Transactional(rollbackFor = Exception.class)
    public <T> T admitFileUpload(String projectId, String conversationId, String userId, long fileSize,
                                Supplier<T> persistDocument) {
        lockProject(projectId);
        assertFileUpload(projectId, conversationId, userId, fileSize);
        return persistDocument.get();
    }

    public void assertFileUpload(String projectId, String conversationId, String userId, long fileSize) {
        AiProjectGovernanceDTO limits = get(projectId);
        if (fileSize > limits.getSingleFileLimit()) {
            throw new MSException("单文件超过项目限制：" + limits.getSingleFileLimit() + " bytes");
        }
        if (limits.getUsedFileBytes() + fileSize > limits.getProjectFileQuota()) {
            throw new MSException("项目 AI 来源文件容量不足");
        }
        int sessionCount;
        if (StringUtils.isBlank(conversationId)) {
            sessionCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1) FROM ai_source_document
                    WHERE project_id=? AND create_user=? AND conversation_id IS NULL AND deleted=0
                    """, Integer.class, projectId, userId);
        } else {
            sessionCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1) FROM ai_source_document
                    WHERE project_id=? AND conversation_id=? AND create_user=? AND deleted=0
                    """, Integer.class, projectId, conversationId, userId);
        }
        if (sessionCount >= limits.getSessionFileLimit()) {
            throw new MSException("当前会话来源文件数量已达到上限：" + limits.getSessionFileLimit());
        }
    }

    public void recordUsage(String projectId, String userId, String modelSourceId, String providerName,
                            String requestType, long inputTokens, long outputTokens, long totalTokens,
                            boolean success, long durationMs, String errorCode) {
        recordUsage(projectId, userId, null, null, modelSourceId, providerName, requestType,
                inputTokens, outputTokens, totalTokens, false, success, durationMs, errorCode);
    }

    public void recordUsage(String projectId, String userId, String conversationId, String requestId,
                            String modelSourceId, String providerName, String requestType,
                            long inputTokens, long outputTokens, long totalTokens, boolean tokenEstimated,
                            boolean success, long durationMs, String errorCode) {
        jdbcTemplate.update("""
                INSERT INTO ai_provider_usage
                (id, project_id, user_id, conversation_id, request_id, model_source_id, provider_name,
                 request_type, input_tokens, output_tokens, total_tokens, token_estimated, success,
                 duration_ms, error_code, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, IDGenerator.nextStr(), StringUtils.defaultString(projectId, "SYSTEM"), userId,
                conversationId, requestId, modelSourceId, providerName, requestType, inputTokens,
                outputTokens, totalTokens, tokenEstimated, success, durationMs, errorCode,
                System.currentTimeMillis());
    }

    private long monthlyTokenUsage(String projectId) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(total_tokens), 0) FROM ai_provider_usage
                WHERE project_id=? AND create_time>=?
                """, Long.class, projectId, monthStart());
        return value == null ? 0L : value;
    }

    private long projectFileBytes(String projectId) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(file_size), 0) FROM ai_source_document WHERE project_id=? AND deleted=0
                """, Long.class, projectId);
        return value == null ? 0L : value;
    }

    private int activeTasks(String projectId) {
        int generations = countActiveSafely("""
                SELECT COUNT(1) FROM functional_case_ai_generation
                WHERE project_id=? AND status IN ('PENDING','GENERATING','VALIDATING')
                """, projectId);
        int executions = countActiveSafely("""
                SELECT COUNT(1) FROM ai_execution_task
                WHERE project_id=? AND status IN ('CREATED','WAITING_CONFIRMATION','PREPARING_BROWSER','WAITING_LOGIN','RUNNING','PAUSED','WRITING_BACK')
                """, projectId);
        int caseAgentExecutions = countActiveSafely("""
                SELECT COUNT(1) FROM ai_case_execution
                WHERE project_id=? AND status IN ('CREATED','RUNNING','WAITING_CONFIRMATION')
                """, projectId);
        return generations + executions + caseAgentExecutions;
    }

    private int countActiveSafely(String sql, String projectId) {
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, projectId);
            return count == null ? 0 : count;
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    private long monthStart() {
        return LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public void recordAgentUsage(String projectId, String userId, String conversationId, String requestId,
                                 String connectionId, String deviceId, String provider, long durationMs,
                                 long inputTokens, long outputTokens, boolean usageEstimated,
                                 String status, String errorCode) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO ai_agent_usage
                (id,project_id,user_id,conversation_id,request_id,connection_id,device_id,provider,
                 duration_ms,input_tokens,output_tokens,usage_estimated,status,error_code,create_time)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, IDGenerator.nextStr(), projectId, userId, conversationId, requestId, connectionId,
                deviceId, provider, durationMs, inputTokens, outputTokens, usageEstimated, status,
                errorCode, System.currentTimeMillis());
    }

    private long dayStart() {
        return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void lockProject(String projectId) {
        long now = System.currentTimeMillis();
        jdbcTemplate.update("""
                INSERT IGNORE INTO ai_project_governance
                (project_id, allowed_model_ids, fallback_model_id, max_concurrent_tasks, monthly_token_quota,
                 project_file_quota, session_file_limit, single_file_limit, update_user, update_time)
                VALUES (?, '[]', NULL, ?, ?, ?, ?, ?, 'SYSTEM', ?)
                """, projectId, DEFAULT_MAX_CONCURRENT, DEFAULT_TOKEN_QUOTA, DEFAULT_PROJECT_FILE_QUOTA,
                DEFAULT_SESSION_FILE_LIMIT, DEFAULT_SINGLE_FILE_LIMIT, now);
        jdbcTemplate.queryForObject(
                "SELECT project_id FROM ai_project_governance WHERE project_id=? FOR UPDATE", String.class, projectId);
    }

    private AiProjectGovernanceDTO defaults(String projectId) {
        AiProjectGovernanceDTO dto = new AiProjectGovernanceDTO();
        dto.setProjectId(projectId);
        dto.setMaxConcurrentTasks(DEFAULT_MAX_CONCURRENT);
        dto.setMonthlyTokenQuota(DEFAULT_TOKEN_QUOTA);
        dto.setProjectFileQuota(DEFAULT_PROJECT_FILE_QUOTA);
        dto.setSessionFileLimit(DEFAULT_SESSION_FILE_LIMIT);
        dto.setSingleFileLimit(DEFAULT_SINGLE_FILE_LIMIT);
        dto.setAllowedResourceTypes(List.of("MODEL_API"));
        dto.setAllowedAgentProviders(List.of());
        dto.setAllowPersonalAgent(false);
        dto.setAllowLocalAgentTools(false);
        dto.setMaxAgentConcurrentTasks(1);
        dto.setMaxAgentExecutionMinutes(15);
        dto.setDailyAgentExecutionLimit(50);
        return dto;
    }

    private AiProjectGovernanceDTO fromRow(Map<String, Object> row) {
        AiProjectGovernanceDTO dto = defaults(String.valueOf(row.get("project_id")));
        dto.setAllowedModelIds(JSON.parseArray(StringUtils.defaultString((String) row.get("allowed_model_ids"), "[]"), String.class));
        dto.setFallbackModelId((String) row.get("fallback_model_id"));
        dto.setMaxConcurrentTasks(((Number) row.get("max_concurrent_tasks")).intValue());
        dto.setMonthlyTokenQuota(((Number) row.get("monthly_token_quota")).longValue());
        dto.setProjectFileQuota(((Number) row.get("project_file_quota")).longValue());
        dto.setSessionFileLimit(((Number) row.get("session_file_limit")).intValue());
        dto.setSingleFileLimit(((Number) row.get("single_file_limit")).longValue());
        dto.setAllowedResourceTypes(JSON.parseArray(StringUtils.defaultIfBlank(
                (String) row.get("allowed_resource_types"), "[\"MODEL_API\"]"), String.class));
        dto.setAllowedAgentProviders(JSON.parseArray(StringUtils.defaultString(
                (String) row.get("allowed_agent_providers"), "[]"), String.class));
        dto.setAllowPersonalAgent(booleanValue(row.get("allow_personal_agent")));
        dto.setAllowLocalAgentTools(booleanValue(row.get("allow_local_agent_tools")));
        dto.setMaxAgentConcurrentTasks(((Number) row.get("max_agent_concurrent_tasks")).intValue());
        dto.setMaxAgentExecutionMinutes(((Number) row.get("max_agent_execution_minutes")).intValue());
        dto.setDailyAgentExecutionLimit(((Number) row.get("daily_agent_execution_limit")).intValue());
        return dto;
    }

    private List<String> normalizeAllowlist(List<String> values, List<String> defaults) {
        if (values == null) {
            return defaults;
        }
        return values.stream().filter(StringUtils::isNotBlank).map(StringUtils::upperCase).distinct().toList();
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof Number number && number.intValue() == 1;
    }
}
