package io.metersphere.agent.quality;

import com.fasterxml.jackson.databind.JsonNode;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.impl.DefaultUidGenerator;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

@Service
public class QualityPolicyService {
    private final JdbcTemplate jdbc;
    private final QualityPolicyAccess access;
    private final QualityPolicyValidator validator;
    private final DefaultUidGenerator ids;

    public QualityPolicyService(JdbcTemplate jdbc, QualityPolicyAccess access, QualityPolicyValidator validator, DefaultUidGenerator ids) {
        this.jdbc = jdbc;
        this.access = access;
        this.validator = validator;
        this.ids = ids;
    }

    public record Policy(String id, String projectId, int versionNo, String status, String rulesJson,
                         String contentHash, long rowVersion, String createdBy, long createdAt,
                         String publishedBy, Long publishedAt, String changeReason) { }
    public record Listing(List<Policy> items, String currentPolicyId, Policy currentPolicy, long total) { }
    public record Publication(String previousPolicyId, String previousHash, String publishedHash,
                              String actor, long publishedAt, String reason) { }
    public record Detail(Policy policy, Publication publication) { }
    public record DraftRequest(@NotBlank @Size(max=64) String projectId,
                               @NotBlank @Size(max=16384) String rulesJson,
                               @PositiveOrZero Long expectedVersion) {
        public String getProjectId() { return projectId; }
    }
    public record PublishRequest(@NotBlank @Size(max=64) String projectId,
                                 @NotNull @PositiveOrZero Long expectedVersion,
                                 @Size(max=64) String expectedCurrentPolicyId,
                                 @NotBlank @Size(max=1000) String changeReason) {
        public String getProjectId() { return projectId; }
    }

    public JsonNode schema(String projectId) {
        access.require(projectId, PermissionConstants.QUALITY_READ);
        return validator.schema();
    }

    public QualityPolicyValidator.Validation validate(DraftRequest request) {
        access.require(request.projectId(), PermissionConstants.QUALITY_POLICY_MANAGE);
        return validator.validate(request.rulesJson());
    }

    @Transactional(readOnly = true)
    public Listing list(String projectId) {
        return list(projectId, 1, 100);
    }

    @Transactional(readOnly = true)
    public Listing list(String projectId, int page, int pageSize) {
        access.require(projectId, PermissionConstants.QUALITY_READ);
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new MSException("VALIDATION_ERROR");
        List<String> current = jdbc.query("SELECT current_policy_id FROM execution_quality_policy_project WHERE project_id=?",
                (rs, n) -> rs.getString(1), projectId);
        String currentId = current.isEmpty() ? null : current.getFirst();
        return new Listing(jdbc.query("SELECT * FROM execution_quality_policy WHERE project_id=? ORDER BY version_no DESC LIMIT ? OFFSET ?",
                this::map, projectId, pageSize, (long) (page - 1) * pageSize), currentId,
                currentId == null ? null : get(currentId, projectId),
                jdbc.queryForObject("SELECT COUNT(*) FROM execution_quality_policy WHERE project_id=?", Long.class, projectId));
    }

    @Transactional(readOnly = true)
    public Detail detail(String id, String projectId) {
        access.require(projectId, PermissionConstants.QUALITY_READ);
        Policy policy = get(id, projectId);
        var events = jdbc.query("SELECT * FROM execution_quality_policy_publication WHERE policy_id=? AND project_id=?",
                (rs, n) -> new Publication(rs.getString("previous_policy_id"), rs.getString("previous_hash"),
                        rs.getString("published_hash"), rs.getString("actor"), rs.getLong("published_at"), rs.getString("reason")), id, projectId);
        return new Detail(policy, events.isEmpty() ? null : events.getFirst());
    }

    @Transactional(rollbackFor = Exception.class)
    public Policy create(DraftRequest request) {
        String actor = access.require(request.projectId(), PermissionConstants.QUALITY_POLICY_MANAGE);
        var validated = requireValid(request.rulesJson());
        // Duplicate-key UPDATE takes an exclusive row lock; INSERT IGNORE can deadlock
        // when concurrent first drafts upgrade duplicate-check shared locks.
        jdbc.update("INSERT INTO execution_quality_policy_project(project_id) VALUES (?) ON DUPLICATE KEY UPDATE next_version=next_version", request.projectId());
        Integer version = jdbc.queryForObject("SELECT next_version FROM execution_quality_policy_project WHERE project_id=? FOR UPDATE",
                Integer.class, request.projectId());
        jdbc.update("UPDATE execution_quality_policy_project SET next_version=next_version+1 WHERE project_id=?", request.projectId());
        String id = String.valueOf(ids.getUID());
        long now = System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO execution_quality_policy(id,project_id,version_no,schema_version,status,rules_json,
                    content_hash,row_version,created_by,created_at,updated_by,updated_at)
                VALUES (?,?,?,'quality-policy.v1','DRAFT',?,?,0,?,?,?,?)
                """, id, request.projectId(), version, validated.normalizedJson(), validated.contentHash(), actor, now, actor, now);
        return get(id, request.projectId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Policy update(String id, DraftRequest request) {
        String actor = access.require(request.projectId(), PermissionConstants.QUALITY_POLICY_MANAGE);
        var validated = requireValid(request.rulesJson());
        if (request.expectedVersion() == null) throw new MSException("VALIDATION_ERROR");
        get(id, request.projectId());
        int count = jdbc.update("""
                UPDATE execution_quality_policy SET rules_json=?,content_hash=?,row_version=row_version+1,updated_by=?,updated_at=?
                WHERE id=? AND project_id=? AND status='DRAFT' AND row_version=?
                """, validated.normalizedJson(), validated.contentHash(), actor, System.currentTimeMillis(),
                id, request.projectId(), request.expectedVersion());
        if (count != 1) throw new MSException("QUALITY_POLICY_VERSION_CONFLICT");
        return get(id, request.projectId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Policy publish(String id, PublishRequest request) {
        String actor = access.require(request.projectId(), PermissionConstants.QUALITY_POLICY_PUBLISH);
        List<String> current = jdbc.query("SELECT current_policy_id FROM execution_quality_policy_project WHERE project_id=? FOR UPDATE",
                (rs,n) -> rs.getString(1), request.projectId());
        if (current.isEmpty()) throw new MSException("QUALITY_POLICY_NOT_FOUND");
        Policy policy = get(id, request.projectId());
        if (!Objects.equals(current.getFirst(), request.expectedCurrentPolicyId())
                || !"DRAFT".equals(policy.status()) || request.expectedVersion() == null
                || policy.rowVersion() != request.expectedVersion()) {
            throw new MSException("QUALITY_POLICY_VERSION_CONFLICT");
        }
        if (request.changeReason() == null || request.changeReason().isBlank() || request.changeReason().length() > 1000) {
            throw new MSException("VALIDATION_ERROR");
        }
        requireValid(policy.rulesJson());
        long now = System.currentTimeMillis();
        int count = jdbc.update("""
                UPDATE execution_quality_policy SET status='PUBLISHED',published_by=?,published_at=?,change_reason=?,
                    updated_by=?,updated_at=?,row_version=row_version+1
                WHERE id=? AND project_id=? AND status='DRAFT' AND row_version=?
                """, actor, now, request.changeReason().trim(), actor, now, id, request.projectId(), request.expectedVersion());
        if (count != 1) throw new MSException("QUALITY_POLICY_VERSION_CONFLICT");
        Policy previous = current.getFirst() == null ? null : get(current.getFirst(), request.projectId());
        jdbc.update("""
                INSERT INTO execution_quality_policy_publication
                    (policy_id,project_id,previous_policy_id,previous_hash,published_hash,actor,published_at,reason)
                VALUES (?,?,?,?,?,?,?,?)
                """, id, request.projectId(), previous == null ? null : previous.id(),
                previous == null ? null : previous.contentHash(), policy.contentHash(), actor, now, request.changeReason().trim());
        jdbc.update("UPDATE execution_quality_policy_project SET current_policy_id=?,row_version=row_version+1 WHERE project_id=?",
                id, request.projectId());
        return get(id, request.projectId());
    }

    private QualityPolicyValidator.Validation requireValid(String json) {
        var result = validator.validate(json);
        if (!result.valid()) throw new QualityPolicyValidationException(result.errors());
        return result;
    }

    private Policy get(String id, String projectId) {
        var result = jdbc.query("SELECT * FROM execution_quality_policy WHERE id=? AND project_id=?", this::map, id, projectId);
        if (result.isEmpty()) throw new MSException("QUALITY_POLICY_NOT_FOUND");
        return result.getFirst();
    }

    private Policy map(ResultSet rs, int row) throws SQLException {
        return new Policy(rs.getString("id"), rs.getString("project_id"), rs.getInt("version_no"), rs.getString("status"),
                rs.getString("rules_json"), rs.getString("content_hash"), rs.getLong("row_version"),
                rs.getString("created_by"), rs.getLong("created_at"), rs.getString("published_by"),
                rs.getObject("published_at", Long.class), rs.getString("change_reason"));
    }
}
