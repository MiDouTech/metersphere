package io.metersphere.functional.repository;

import io.metersphere.functional.dto.AiCaseConversationDTO;
import io.metersphere.functional.dto.AiCaseExecutionDTO;
import io.metersphere.functional.dto.AiCaseExecutionEventDTO;
import io.metersphere.functional.dto.AiCaseMessageDTO;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

@Repository
public class AiCaseAgentRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    public void insertConversation(AiCaseConversationDTO conversation) {
        jdbcTemplate.update("""
                        INSERT INTO ai_case_conversation
                        (id, project_id, organization_id, user_id, title, model_source_id, status,
                         system_prompt_version, last_message_time, create_time, update_time, deleted)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                conversation.getId(), conversation.getProjectId(), conversation.getOrganizationId(),
                conversation.getUserId(), conversation.getTitle(), conversation.getModelSourceId(),
                conversation.getStatus(), conversation.getSystemPromptVersion(), conversation.getLastMessageTime(),
                conversation.getCreateTime(), conversation.getUpdateTime());
    }

    public AiCaseConversationDTO findConversation(String id, String projectId, String userId) {
        List<AiCaseConversationDTO> rows = jdbcTemplate.query("""
                        SELECT * FROM ai_case_conversation
                        WHERE id=? AND project_id=? AND user_id=? AND deleted=0
                        """, this::mapConversation, id, projectId, userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean lockConversation(String id, String projectId, String userId) {
        List<String> rows = jdbcTemplate.queryForList("""
                SELECT id FROM ai_case_conversation
                WHERE id=? AND project_id=? AND user_id=? AND deleted=0 FOR UPDATE
                """, String.class, id, projectId, userId);
        return !rows.isEmpty();
    }

    public long countConversations(String projectId, String userId, String status) {
        String statusClause = status == null ? "" : " AND status=?";
        Object[] args = status == null ? new Object[]{projectId, userId} : new Object[]{projectId, userId, status};
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM ai_case_conversation
                WHERE project_id=? AND user_id=? AND deleted=0
                """ + statusClause, Long.class, args);
        return value == null ? 0 : value;
    }

    public List<AiCaseConversationDTO> pageConversations(String projectId, String userId, String status,
                                                          long offset, int pageSize) {
        String statusClause = status == null ? "" : " AND status=?";
        String sql = """
                SELECT * FROM ai_case_conversation
                WHERE project_id=? AND user_id=? AND deleted=0
                """ + statusClause + " ORDER BY COALESCE(last_message_time, update_time) DESC, id DESC LIMIT ? OFFSET ?";
        if (status == null) {
            return jdbcTemplate.query(sql, this::mapConversation, projectId, userId, pageSize, offset);
        }
        return jdbcTemplate.query(sql, this::mapConversation, projectId, userId, status, pageSize, offset);
    }

    public int updateConversationTitle(String id, String projectId, String userId, String title, long updateTime) {
        return jdbcTemplate.update("""
                UPDATE ai_case_conversation SET title=?, update_time=?
                WHERE id=? AND project_id=? AND user_id=? AND deleted=0
                """, title, updateTime, id, projectId, userId);
    }

    public int updateConversationModel(String id, String projectId, String userId, String modelSourceId, long updateTime) {
        return jdbcTemplate.update("""
                UPDATE ai_case_conversation SET model_source_id=?, update_time=?
                WHERE id=? AND project_id=? AND user_id=? AND deleted=0
                """, modelSourceId, updateTime, id, projectId, userId);
    }

    public int updateConversationStatus(String id, String projectId, String userId, String status, long updateTime) {
        return jdbcTemplate.update("""
                UPDATE ai_case_conversation SET status=?, update_time=?
                WHERE id=? AND project_id=? AND user_id=? AND deleted=0
                """, status, updateTime, id, projectId, userId);
    }

    public int softDeleteConversation(String id, String projectId, String userId, long updateTime) {
        return jdbcTemplate.update("""
                UPDATE ai_case_conversation SET status='DELETED', deleted=1, update_time=?
                WHERE id=? AND project_id=? AND user_id=? AND deleted=0
                """, updateTime, id, projectId, userId);
    }

    public void insertMessage(AiCaseMessageDTO message) {
        jdbcTemplate.update("""
                        INSERT INTO ai_case_message
                        (id, conversation_id, project_id, user_id, role, content, status, model_source_id,
                         request_id, tool_name, tool_call_id, tool_arguments, tool_result, input_tokens,
                         output_tokens, token_estimated, error_code, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                message.getId(), message.getConversationId(), message.getProjectId(), message.getUserId(),
                message.getRole(), message.getContent(), message.getStatus(), message.getModelSourceId(),
                message.getRequestId(), message.getToolName(), message.getToolCallId(), message.getToolArguments(),
                message.getToolResult(), defaultLong(message.getInputTokens()), defaultLong(message.getOutputTokens()),
                Boolean.TRUE.equals(message.getTokenEstimated()), message.getErrorCode(), message.getCreateTime(),
                message.getUpdateTime());
        jdbcTemplate.update("""
                UPDATE ai_case_conversation SET last_message_time=?, update_time=?
                WHERE id=? AND project_id=? AND user_id=? AND deleted=0
                """, message.getCreateTime(), message.getCreateTime(), message.getConversationId(),
                message.getProjectId(), message.getUserId());
    }

    public List<AiCaseMessageDTO> listMessages(String conversationId, String projectId, String userId,
                                               Long beforeTime, String beforeId, int limit) {
        if (beforeTime == null || beforeId == null) {
            return jdbcTemplate.query("""
                            SELECT * FROM ai_case_message
                            WHERE conversation_id=? AND project_id=? AND user_id=?
                            ORDER BY create_time DESC, id DESC LIMIT ?
                            """, this::mapMessage, conversationId, projectId, userId, limit);
        }
        return jdbcTemplate.query("""
                        SELECT * FROM ai_case_message
                        WHERE conversation_id=? AND project_id=? AND user_id=?
                          AND (create_time < ? OR (create_time=? AND id < ?))
                        ORDER BY create_time DESC, id DESC LIMIT ?
                        """, this::mapMessage, conversationId, projectId, userId,
                beforeTime, beforeTime, beforeId, limit);
    }

    public AiCaseMessageDTO findMessage(String id, String projectId, String userId) {
        List<AiCaseMessageDTO> rows = jdbcTemplate.query("""
                SELECT * FROM ai_case_message WHERE id=? AND project_id=? AND user_id=?
                """, this::mapMessage, id, projectId, userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void insertExecution(AiCaseExecutionDTO execution) {
        jdbcTemplate.update("""
                        INSERT INTO ai_case_execution
                        (id, request_id, conversation_id, project_id, user_id, user_message_id,
                         assistant_message_id, execution_type, status, requested_model_source_id,
                         actual_model_source_id, cancel_requested, retry_of_request_id, input_tokens,
                         output_tokens, token_estimated, error_code, error_message, start_time,
                         first_token_time, finish_time, duration_ms, event_sequence, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                execution.getId(), execution.getRequestId(), execution.getConversationId(), execution.getProjectId(),
                execution.getUserId(), execution.getUserMessageId(), execution.getAssistantMessageId(),
                execution.getExecutionType(), execution.getStatus(), execution.getRequestedModelSourceId(),
                execution.getActualModelSourceId(), Boolean.TRUE.equals(execution.getCancelRequested()),
                execution.getRetryOfRequestId(), defaultLong(execution.getInputTokens()),
                defaultLong(execution.getOutputTokens()), Boolean.TRUE.equals(execution.getTokenEstimated()),
                execution.getErrorCode(), execution.getErrorMessage(), execution.getStartTime(),
                execution.getFirstTokenTime(), execution.getFinishTime(), execution.getDurationMs(),
                defaultLong(execution.getEventSequence()), execution.getCreateTime(), execution.getUpdateTime());
    }

    public AiCaseExecutionDTO findExecution(String requestId, String projectId, String userId) {
        List<AiCaseExecutionDTO> rows = jdbcTemplate.query("""
                        SELECT * FROM ai_case_execution WHERE request_id=? AND project_id=? AND user_id=?
                        """, this::mapExecution, requestId, projectId, userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public int countActiveExecutions(String conversationId, String projectId, String userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM ai_case_execution
                WHERE conversation_id=? AND project_id=? AND user_id=?
                  AND status IN ('CREATED','RUNNING','WAITING_CONFIRMATION')
                """, Integer.class, conversationId, projectId, userId);
        return count == null ? 0 : count;
    }

    public int requestCancel(String requestId, String projectId, String userId, long updateTime) {
        return jdbcTemplate.update("""
                UPDATE ai_case_execution SET cancel_requested=1, update_time=?
                WHERE request_id=? AND project_id=? AND user_id=?
                  AND status IN ('CREATED','RUNNING','WAITING_CONFIRMATION')
                """, updateTime, requestId, projectId, userId);
    }

    public boolean isCancelRequested(String requestId) {
        Boolean canceled = jdbcTemplate.queryForObject("""
                SELECT cancel_requested FROM ai_case_execution WHERE request_id=?
                """, Boolean.class, requestId);
        return Boolean.TRUE.equals(canceled);
    }

    public int markExecutionRunning(String requestId, String projectId, String userId, long startTime) {
        return jdbcTemplate.update("""
                UPDATE ai_case_execution
                SET status='RUNNING', start_time=?, update_time=?
                WHERE request_id=? AND project_id=? AND user_id=? AND status='CREATED'
                """, startTime, startTime, requestId, projectId, userId);
    }

    public int markFirstToken(String requestId, long firstTokenTime) {
        return jdbcTemplate.update("""
                UPDATE ai_case_execution SET first_token_time=COALESCE(first_token_time, ?), update_time=?
                WHERE request_id=? AND status='RUNNING'
                """, firstTokenTime, firstTokenTime, requestId);
    }

    public int updateActualModel(String requestId, String projectId, String userId,
                                 String modelSourceId, long updateTime) {
        return jdbcTemplate.update("""
                UPDATE ai_case_execution SET actual_model_source_id=?, update_time=?
                WHERE request_id=? AND project_id=? AND user_id=? AND status='RUNNING'
                """, modelSourceId, updateTime, requestId, projectId, userId);
    }

    public int completeExecution(String requestId, String projectId, String userId, String status,
                                 long inputTokens, long outputTokens, boolean tokenEstimated,
                                 String errorCode, String errorMessage, long finishTime, long durationMs) {
        return jdbcTemplate.update("""
                UPDATE ai_case_execution
                SET status=?, input_tokens=?, output_tokens=?, token_estimated=?, error_code=?, error_message=?,
                    finish_time=?, duration_ms=?, update_time=?
                WHERE request_id=? AND project_id=? AND user_id=?
                  AND status IN ('CREATED','RUNNING','WAITING_CONFIRMATION')
                """, status, inputTokens, outputTokens, tokenEstimated, errorCode, errorMessage,
                finishTime, durationMs, finishTime, requestId, projectId, userId);
    }

    public int completeMessage(String messageId, String projectId, String userId, String status,
                               String content, String modelSourceId, long inputTokens, long outputTokens,
                               boolean tokenEstimated, String errorCode, long updateTime) {
        return jdbcTemplate.update("""
                UPDATE ai_case_message
                SET status=?, content=?, model_source_id=?, input_tokens=?, output_tokens=?, token_estimated=?,
                    error_code=?, update_time=?
                WHERE id=? AND project_id=? AND user_id=? AND status='STREAMING'
                """, status, content, modelSourceId, inputTokens, outputTokens, tokenEstimated,
                errorCode, updateTime, messageId, projectId, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public AiCaseExecutionEventDTO appendEvent(String requestId, String eventType, String payload, long createTime) {
        int affected = jdbcTemplate.update("""
                UPDATE ai_case_execution SET event_sequence=event_sequence+1, update_time=? WHERE request_id=?
                """, createTime, requestId);
        if (affected == 0) {
            return null;
        }
        Long sequence = jdbcTemplate.queryForObject(
                "SELECT event_sequence FROM ai_case_execution WHERE request_id=?", Long.class, requestId);
        AiCaseExecutionEventDTO event = new AiCaseExecutionEventDTO();
        event.setId(IDGenerator.nextStr());
        event.setRequestId(requestId);
        event.setSequence(sequence);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setCreateTime(createTime);
        event.setTimestamp(createTime);
        jdbcTemplate.update("""
                INSERT INTO ai_case_execution_event (id, request_id, sequence_no, event_type, payload, create_time)
                VALUES (?, ?, ?, ?, ?, ?)
                """, event.getId(), requestId, sequence, eventType, payload, createTime);
        return event;
    }

    public List<AiCaseExecutionEventDTO> listEvents(String requestId, long afterSequence, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        return jdbcTemplate.query("""
                        SELECT * FROM ai_case_execution_event
                        WHERE request_id=? AND sequence_no>? ORDER BY sequence_no ASC LIMIT ?
                """, this::mapEvent, requestId, afterSequence, limit);
    }

    public String findSucceededToolResult(String conversationId, String toolCallId,
                                          String projectId, String userId) {
        List<String> rows = jdbcTemplate.queryForList("""
                SELECT result_json FROM ai_case_tool_call
                WHERE conversation_id=? AND tool_call_id=? AND project_id=? AND user_id=? AND status='SUCCEEDED'
                """, String.class, conversationId, toolCallId, projectId, userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public int insertToolCall(String id, String requestId, String conversationId, String projectId,
                              String userId, String toolCallId, String toolName, String argumentsHash,
                              String argumentsJson, boolean confirmationRequired, long now) {
        return jdbcTemplate.update("""
                INSERT IGNORE INTO ai_case_tool_call
                (id, request_id, conversation_id, project_id, user_id, tool_call_id, tool_name,
                 arguments_hash, arguments_json, status, confirmation_required, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?)
                """, id, requestId, conversationId, projectId, userId, toolCallId, toolName,
                argumentsHash, argumentsJson, confirmationRequired, now, now);
    }

    public int completeToolCall(String conversationId, String toolCallId, String projectId, String userId,
                                String status, String resultJson, String errorCode, long now) {
        return jdbcTemplate.update("""
                UPDATE ai_case_tool_call SET status=?, result_json=?, error_code=?, update_time=?
                WHERE conversation_id=? AND tool_call_id=? AND project_id=? AND user_id=? AND status='RUNNING'
                """, status, resultJson, errorCode, now, conversationId, toolCallId, projectId, userId);
    }

    private AiCaseConversationDTO mapConversation(ResultSet rs, int rowNum) throws SQLException {
        AiCaseConversationDTO dto = new AiCaseConversationDTO();
        dto.setId(rs.getString("id"));
        dto.setProjectId(rs.getString("project_id"));
        dto.setOrganizationId(rs.getString("organization_id"));
        dto.setUserId(rs.getString("user_id"));
        dto.setTitle(rs.getString("title"));
        dto.setModelSourceId(rs.getString("model_source_id"));
        dto.setStatus(rs.getString("status"));
        dto.setSystemPromptVersion(rs.getString("system_prompt_version"));
        dto.setLastMessageTime(nullableLong(rs, "last_message_time"));
        dto.setCreateTime(rs.getLong("create_time"));
        dto.setUpdateTime(rs.getLong("update_time"));
        return dto;
    }

    private AiCaseMessageDTO mapMessage(ResultSet rs, int rowNum) throws SQLException {
        AiCaseMessageDTO dto = new AiCaseMessageDTO();
        dto.setId(rs.getString("id"));
        dto.setConversationId(rs.getString("conversation_id"));
        dto.setProjectId(rs.getString("project_id"));
        dto.setUserId(rs.getString("user_id"));
        dto.setRole(rs.getString("role"));
        dto.setContent(rs.getString("content"));
        dto.setStatus(rs.getString("status"));
        dto.setModelSourceId(rs.getString("model_source_id"));
        dto.setRequestId(rs.getString("request_id"));
        dto.setToolName(rs.getString("tool_name"));
        dto.setToolCallId(rs.getString("tool_call_id"));
        dto.setToolArguments(rs.getString("tool_arguments"));
        dto.setToolResult(rs.getString("tool_result"));
        dto.setInputTokens(rs.getLong("input_tokens"));
        dto.setOutputTokens(rs.getLong("output_tokens"));
        dto.setTokenEstimated(rs.getBoolean("token_estimated"));
        dto.setErrorCode(rs.getString("error_code"));
        dto.setCreateTime(rs.getLong("create_time"));
        dto.setUpdateTime(rs.getLong("update_time"));
        return dto;
    }

    private AiCaseExecutionDTO mapExecution(ResultSet rs, int rowNum) throws SQLException {
        AiCaseExecutionDTO dto = new AiCaseExecutionDTO();
        dto.setId(rs.getString("id"));
        dto.setRequestId(rs.getString("request_id"));
        dto.setConversationId(rs.getString("conversation_id"));
        dto.setProjectId(rs.getString("project_id"));
        dto.setUserId(rs.getString("user_id"));
        dto.setUserMessageId(rs.getString("user_message_id"));
        dto.setAssistantMessageId(rs.getString("assistant_message_id"));
        dto.setExecutionType(rs.getString("execution_type"));
        dto.setStatus(rs.getString("status"));
        dto.setRequestedModelSourceId(rs.getString("requested_model_source_id"));
        dto.setActualModelSourceId(rs.getString("actual_model_source_id"));
        dto.setCancelRequested(rs.getBoolean("cancel_requested"));
        dto.setRetryOfRequestId(rs.getString("retry_of_request_id"));
        dto.setInputTokens(rs.getLong("input_tokens"));
        dto.setOutputTokens(rs.getLong("output_tokens"));
        dto.setTokenEstimated(rs.getBoolean("token_estimated"));
        dto.setErrorCode(rs.getString("error_code"));
        dto.setErrorMessage(rs.getString("error_message"));
        dto.setStartTime(nullableLong(rs, "start_time"));
        dto.setFirstTokenTime(nullableLong(rs, "first_token_time"));
        dto.setFinishTime(nullableLong(rs, "finish_time"));
        dto.setDurationMs(nullableLong(rs, "duration_ms"));
        dto.setEventSequence(rs.getLong("event_sequence"));
        dto.setCreateTime(rs.getLong("create_time"));
        dto.setUpdateTime(rs.getLong("update_time"));
        return dto;
    }

    private AiCaseExecutionEventDTO mapEvent(ResultSet rs, int rowNum) throws SQLException {
        AiCaseExecutionEventDTO dto = new AiCaseExecutionEventDTO();
        dto.setId(rs.getString("id"));
        dto.setRequestId(rs.getString("request_id"));
        dto.setSequence(rs.getLong("sequence_no"));
        dto.setEventType(rs.getString("event_type"));
        dto.setPayload(rs.getString("payload"));
        dto.setCreateTime(rs.getLong("create_time"));
        dto.setTimestamp(dto.getCreateTime());
        return dto;
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
