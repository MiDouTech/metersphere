package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentTempAttachmentUploadResponse;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

/** Ordinary case attachments only. Execution evidence keeps its independent submission contract. */
@Service
public class AgentAttachmentUploadIntentService {
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentBusinessAccess access;
    @Resource private AgentTempAttachmentService attachments;
    public record Prepare(String projectId, String purpose, String fileName, String contentType, long size, String sha256, String requestId) { }
    public record Prepared(String uploadId, long expiresAt, String uploadPath, long maximumBytes) { }

    @Transactional(rollbackFor = Exception.class)
    public Prepared prepare(Prepare request) {
        if (request == null || !Set.of("CASE_DETAIL", "CASE_COMMENT", "BUG_DETAIL", "BUG_COMMENT").contains(Objects.toString(request.purpose(), ""))
                || request.size() < 1 || request.size() > 5L * 1024 * 1024
                || request.sha256() == null || !request.sha256().matches("[a-fA-F0-9]{64}")
                || request.requestId() == null || !request.requestId().matches("[A-Za-z0-9_.:-]{1,128}")
                || request.fileName() == null || request.fileName().isBlank() || request.fileName().length() > 200
                || request.fileName().contains("/") || request.fileName().contains("\\") || request.fileName().chars().anyMatch(Character::isISOControl)
                || request.contentType() == null || !request.contentType().matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))
            throw new MSException("附件参数无效；请提供文件名、类型、大小、SHA-256及请求标识，单文件最多5MiB");
        authorize(request.projectId(),request.purpose());
        var token = AgentTokenContext.get();
        jdbc.queryForList("SELECT id FROM agent_token WHERE id=? FOR UPDATE", token.getId());
        String legacyHash = DigestUtils.sha256Hex(JSON.toJSONString(request));
        var canonical = new Prepare(request.projectId(),request.purpose(),request.fileName(),request.contentType(),request.size(),request.sha256().toLowerCase(Locale.ROOT),request.requestId());
        String hash = DigestUtils.sha256Hex(JSON.toJSONString(canonical));
        var prior = jdbc.queryForList("SELECT * FROM agent_attachment_upload_intent WHERE token_id=? AND (request_id=? OR (identity_version='legacy-ci-v1' AND request_id COLLATE utf8mb4_general_ci=? COLLATE utf8mb4_general_ci))", token.getId(), request.requestId(), request.requestId());
        if(prior.size()>1)throw new MSException("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");
        if (!prior.isEmpty()) {
            var row = prior.getFirst();
            if (!hash.equals(row.get("request_hash")) && !legacyHash.equals(row.get("request_hash"))) throw new MSException("请求标识已用于其他文件，请使用新的请求标识");
            if (((Number) row.get("expires_at")).longValue() <= System.currentTimeMillis()) throw new MSException("上传准备已过期，请使用新的请求标识");
            return prepared((String) row.get("id"), ((Number) row.get("expires_at")).longValue());
        }
        long now = System.currentTimeMillis();
        Long reserved = jdbc.queryForObject("SELECT COALESCE(SUM(expected_size),0) FROM agent_attachment_upload_intent WHERE token_id=? AND expires_at>?", Long.class, token.getId(), now);
        if (reserved != null && reserved + request.size() > 50L * 1024 * 1024) throw new MSException("当前上传额度已满，请等待暂存上传过期后重试");
        String id = UUID.randomUUID().toString(); long expires = now + 15 * 60_000L;
        jdbc.update("""
            INSERT INTO agent_attachment_upload_intent(id,token_id,user_id,project_id,request_id,request_hash,file_name,content_type,expected_size,expected_sha256,purpose,expires_at,create_time,identity_version)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'binary-v2')
            """, id, token.getId(), token.getUserId(), request.projectId(), request.requestId(), hash, request.fileName(), request.contentType(), request.size(), request.sha256().toLowerCase(Locale.ROOT), request.purpose(), expires, now);
        return prepared(id, expires);
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentTempAttachmentUploadResponse complete(String id, MultipartFile file) throws java.io.IOException {
        var token = Objects.requireNonNull(AgentTokenContext.get());
        var rows = jdbc.queryForList("SELECT * FROM agent_attachment_upload_intent WHERE id=? AND token_id=? AND user_id=? FOR UPDATE", id, token.getId(), token.getUserId());
        if (rows.size() != 1) throw AgentBusinessAccess.denied();
        var row = rows.getFirst();
        authorize((String) row.get("project_id"),(String) row.get("purpose"));

        if (file == null || file.getSize() != ((Number) row.get("expected_size")).longValue()
                || !Objects.equals(file.getOriginalFilename(), row.get("file_name")) || !Objects.equals(file.getContentType(), row.get("content_type")))
            throw new MSException("上传文件与准备信息不一致");
        try (var stream = file.getInputStream()) {
            if (!DigestUtils.sha256Hex(stream).equals(row.get("expected_sha256"))) throw new MSException("上传文件摘要不一致，请重新选择原文件");
        }
        if ("COMPLETED".equals(row.get("state"))) return JSON.parseObject((String) row.get("result_json"), AgentTempAttachmentUploadResponse.class);
        if (((Number) row.get("expires_at")).longValue() <= System.currentTimeMillis()) throw new MSException("ATTACHMENT_EXPIRED");
        var result = attachments.upload(file, (String) row.get("project_id"), (String) row.get("purpose"), null);
        jdbc.update("UPDATE agent_attachment_upload_intent SET state='COMPLETED',result_json=? WHERE id=?", JSON.toJSONString(result), id);
        return result;
    }

    private Prepared prepared(String id, long expires) {
        return new Prepared(id, expires, "/api/agent/v1/attachment/upload/complete/" + id, 5L * 1024 * 1024);
    }
    private void authorize(String projectId,String purpose) {
        boolean bug=Set.of("BUG_DETAIL","BUG_COMMENT").contains(purpose);
        access.project(projectId,bug?"PROJECT_BUG:READ+UPDATE":"FUNCTIONAL_CASE:READ",true,
                bug?AgentTokenScope.BUG_ATTACHMENT:AgentTokenScope.CASE_ATTACHMENT);
    }
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanup() { jdbc.update("DELETE FROM agent_attachment_upload_intent WHERE expires_at<? AND state='PREPARED' LIMIT 100", System.currentTimeMillis()); }
}
