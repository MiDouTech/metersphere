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
public class GlobalQualityPolicyService {
    private final JdbcTemplate jdbc;
    private final QualityPolicyAccess access;
    private final QualityPolicyValidator validator;
    private final DefaultUidGenerator ids;

    public GlobalQualityPolicyService(JdbcTemplate jdbc, QualityPolicyAccess access, QualityPolicyValidator validator, DefaultUidGenerator ids) {
        this.jdbc = jdbc;
        this.access = access;
        this.validator = validator;
        this.ids = ids;
    }

    public record Policy(String id, String scope, int versionNo, String status, String rulesJson,
                         String contentHash, long rowVersion, String createdBy, long createdAt,
                         String publishedBy, Long publishedAt, String changeReason) { }
    public record Listing(List<Policy> items, String currentPolicyId, Policy currentPolicy, long total) { }
    public record Publication(String previousPolicyId, String previousHash, String publishedHash,
                              String actor, long publishedAt, String reason, String traceId) { }
    public record Detail(Policy policy, Publication publication) { }
    public record DraftRequest(
                               @NotBlank @Size(max=16384) String rulesJson,
                               @PositiveOrZero Long expectedVersion) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name, JsonNode value) { throw new IllegalArgumentException("Unsupported field"); }
    }
    public record PublishRequest(
                                 @NotNull @PositiveOrZero Long expectedVersion,
                                 @Size(max=64) String expectedCurrentPolicyId,
                                 @NotBlank @Size(max=1000) String changeReason) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name, JsonNode value) { throw new IllegalArgumentException("Unsupported field"); }
    }

    public JsonNode schema() {
        access.requireSystem(PermissionConstants.SYSTEM_QUALITY_READ);
        return validator.schema();
    }

    public QualityPolicyValidator.Validation validate(DraftRequest request) {
        access.requireSystem(PermissionConstants.SYSTEM_QUALITY_MANAGE);
        return validator.validate(request.rulesJson());
    }

    @Transactional(readOnly = true)
    public Listing list() {
        return list(1, 100);
    }

    @Transactional(readOnly = true)
    public Listing list(int page, int pageSize) {
        access.requireSystem(PermissionConstants.SYSTEM_QUALITY_READ);
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new MSException("VALIDATION_ERROR");
        List<String> current = jdbc.query("SELECT current_policy_id FROM execution_quality_policy_global_state WHERE scope='GLOBAL'",
                (rs, n) -> rs.getString(1));
        String currentId = current.isEmpty() ? null : current.getFirst();
        return new Listing(jdbc.query("SELECT * FROM execution_quality_policy_global ORDER BY version_no DESC LIMIT ? OFFSET ?",
                this::map, pageSize, (long) (page - 1) * pageSize), currentId,
                currentId == null ? null : get(currentId),
                jdbc.queryForObject("SELECT COUNT(*) FROM execution_quality_policy_global", Long.class));
    }

    @Transactional(readOnly = true)
    public Detail detail(String id) {
        access.requireSystem(PermissionConstants.SYSTEM_QUALITY_READ);
        Policy policy = get(id);
        var events = jdbc.query("SELECT * FROM execution_quality_policy_global_publication WHERE policy_id=?",
                (rs, n) -> new Publication(rs.getString("previous_policy_id"), rs.getString("previous_hash"),
                        rs.getString("published_hash"), rs.getString("actor"), rs.getLong("published_at"), rs.getString("reason"), rs.getString("trace_id")), id);
        return new Detail(policy, events.isEmpty() ? null : events.getFirst());
    }

    @Transactional(rollbackFor = Exception.class)
    public Policy create(DraftRequest request) {
        String actor = access.requireSystem(PermissionConstants.SYSTEM_QUALITY_MANAGE);
        var validated = requireValid(request.rulesJson());
        // Duplicate-key UPDATE takes an exclusive row lock; INSERT IGNORE can deadlock
        // when concurrent first drafts upgrade duplicate-check shared locks.
        jdbc.update("INSERT INTO execution_quality_policy_global_state(scope) VALUES ('GLOBAL') ON DUPLICATE KEY UPDATE next_version=next_version");
        Integer version = jdbc.queryForObject("SELECT next_version FROM execution_quality_policy_global_state WHERE scope='GLOBAL' FOR UPDATE",
                Integer.class);
        jdbc.update("UPDATE execution_quality_policy_global_state SET next_version=next_version+1 WHERE scope='GLOBAL'");
        String id = String.valueOf(ids.getUID());
        long now = System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO execution_quality_policy_global(id,version_no,schema_version,status,rules_json,
                    content_hash,row_version,created_by,created_at,updated_by,updated_at)
                VALUES (?,?,'quality-policy.v1','DRAFT',?,?,0,?,?,?,?)
                """, id, version, validated.normalizedJson(), validated.contentHash(), actor, now, actor, now);
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Policy update(String id, DraftRequest request) {
        String actor = access.requireSystem(PermissionConstants.SYSTEM_QUALITY_MANAGE);
        var validated = requireValid(request.rulesJson());
        if (request.expectedVersion() == null) throw new MSException("VALIDATION_ERROR");
        get(id);
        int count = jdbc.update("""
                UPDATE execution_quality_policy_global SET rules_json=?,content_hash=?,row_version=row_version+1,updated_by=?,updated_at=?
                WHERE id=? AND status='DRAFT' AND row_version=?
                """, validated.normalizedJson(), validated.contentHash(), actor, System.currentTimeMillis(),
                id, request.expectedVersion());
        if (count != 1) throw new MSException("QUALITY_POLICY_VERSION_CONFLICT");
        return get(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Policy publish(String id, PublishRequest request) {
        String actor = access.requireSystem(PermissionConstants.SYSTEM_QUALITY_PUBLISH);
        List<String> current = jdbc.query("SELECT current_policy_id FROM execution_quality_policy_global_state WHERE scope='GLOBAL' FOR UPDATE",
                (rs,n) -> rs.getString(1));
        if (current.isEmpty()) throw new MSException("QUALITY_POLICY_NOT_FOUND");
        Policy policy = get(id);
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
                UPDATE execution_quality_policy_global SET status='PUBLISHED',published_by=?,published_at=?,change_reason=?,
                    updated_by=?,updated_at=?,row_version=row_version+1
                WHERE id=? AND status='DRAFT' AND row_version=?
                """, actor, now, request.changeReason().trim(), actor, now, id, request.expectedVersion());
        if (count != 1) throw new MSException("QUALITY_POLICY_VERSION_CONFLICT");
        Policy previous = current.getFirst() == null ? null : get(current.getFirst());
        jdbc.update("""
                INSERT INTO execution_quality_policy_global_publication
                    (policy_id,previous_policy_id,previous_hash,published_hash,actor,published_at,reason,trace_id)
                VALUES (?,?,?,?,?,?,?,?)
                """, id, previous == null ? null : previous.id(),
                previous == null ? null : previous.contentHash(), policy.contentHash(), actor, now, request.changeReason().trim(), java.util.UUID.randomUUID().toString());
        jdbc.update("UPDATE execution_quality_policy_global_state SET current_policy_id=?,row_version=row_version+1 WHERE scope='GLOBAL'",
                id);
        return get(id);
    }

    public record LegacyPolicy(String id, String projectId, int versionNo, String status, String contentHash, String rulesJson) {}
    public record Archive(List<LegacyPolicy> items, long total) {}
    public Archive archive(int page, int pageSize) {
        access.requireSystem(PermissionConstants.SYSTEM_QUALITY_READ);
        if(page<1 || pageSize<1 || pageSize>100) throw new MSException("VALIDATION_ERROR");
        return new Archive(jdbc.query("SELECT id,project_id,version_no,status,content_hash,rules_json FROM execution_quality_policy ORDER BY project_id,version_no DESC LIMIT ? OFFSET ?",
                (rs,n)->new LegacyPolicy(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getString(4),rs.getString(5),rs.getString(6)),pageSize,(long)(page-1)*pageSize),
                jdbc.queryForObject("SELECT COUNT(*) FROM execution_quality_policy",Long.class));
    }
    @Transactional(rollbackFor=Exception.class)
    public Policy importLegacy(String legacyId) {
        access.requireSystem(PermissionConstants.SYSTEM_QUALITY_MANAGE);
        var sources=jdbc.queryForList("SELECT rules_json FROM execution_quality_policy WHERE id=?",String.class,legacyId);
        if(sources.isEmpty()) throw new MSException("QUALITY_POLICY_NOT_FOUND");
        var mapped=jdbc.queryForList("SELECT global_policy_id FROM execution_quality_policy_migration_map WHERE legacy_policy_id=? FOR UPDATE",String.class,legacyId);
        if(mapped.isEmpty()) throw new MSException("QUALITY_POLICY_NOT_FOUND");
        if(mapped.getFirst()!=null) return get(mapped.getFirst());
        Policy candidate=create(new DraftRequest(sources.getFirst(),null));
        jdbc.update("UPDATE execution_quality_policy_migration_map SET global_policy_id=?,status='IMPORTED_DRAFT' WHERE legacy_policy_id=?",candidate.id(),legacyId);
        return candidate;
    }

    private QualityPolicyValidator.Validation requireValid(String json) {
        var result = validator.validate(json);
        if (!result.valid()) throw new QualityPolicyValidationException(result.errors());
        return result;
    }

    private Policy get(String id) {
        var result = jdbc.query("SELECT * FROM execution_quality_policy_global WHERE id=?", this::map, id);
        if (result.isEmpty()) throw new MSException("QUALITY_POLICY_NOT_FOUND");
        return result.getFirst();
    }

    private Policy map(ResultSet rs, int row) throws SQLException {
        return new Policy(rs.getString("id"), "SYSTEM", rs.getInt("version_no"), rs.getString("status"),
                rs.getString("rules_json"), rs.getString("content_hash"), rs.getLong("row_version"),
                rs.getString("created_by"), rs.getLong("created_at"), rs.getString("published_by"),
                rs.getObject("published_at", Long.class), rs.getString("change_reason"));
    }
}
