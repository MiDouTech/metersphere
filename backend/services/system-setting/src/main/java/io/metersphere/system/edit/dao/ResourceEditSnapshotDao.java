package io.metersphere.system.edit.dao;

import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ResourceEditSnapshotDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public long nextSeq(String resourceType, String resourceId) {
        Long max = jdbcTemplate.query(
                "SELECT MAX(seq) FROM resource_edit_snapshot WHERE resource_type=? AND resource_id=?",
                rs -> rs.next() ? rs.getLong(1) : 0L,
                resourceType, resourceId);
        return (max == null ? 0L : max) + 1L;
    }

    public void insert(String resourceType, String resourceId, String projectId,
                       long seq, String payload, String contentHash, String createUser) {
        jdbcTemplate.update(
                "INSERT INTO resource_edit_snapshot (id, resource_type, resource_id, project_id, seq, payload, content_hash, create_user, create_time) VALUES (?,?,?,?,?,?,?,?,?)",
                IDGenerator.nextStr(), resourceType, resourceId, projectId, seq,
                payload, contentHash, createUser, System.currentTimeMillis());
    }

    public Map<String, Object> findBySeq(String resourceType, String resourceId, long seq) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT * FROM resource_edit_snapshot WHERE resource_type=? AND resource_id=? AND seq=?",
                resourceType, resourceId, seq);
        return list.isEmpty() ? null : list.getFirst();
    }

    public List<Long> listSeqAsc(String resourceType, String resourceId) {
        return jdbcTemplate.queryForList(
                "SELECT seq FROM resource_edit_snapshot WHERE resource_type=? AND resource_id=? ORDER BY seq ASC",
                Long.class, resourceType, resourceId);
    }

    public void deleteBySeq(String resourceType, String resourceId, long seq) {
        jdbcTemplate.update(
                "DELETE FROM resource_edit_snapshot WHERE resource_type=? AND resource_id=? AND seq=?",
                resourceType, resourceId, seq);
    }

    public void deleteGreaterThan(String resourceType, String resourceId, long seq) {
        jdbcTemplate.update(
                "DELETE FROM resource_edit_snapshot WHERE resource_type=? AND resource_id=? AND seq>?",
                resourceType, resourceId, seq);
    }

    public Long getActiveSeq(String resourceType, String resourceId) {
        List<Long> list = jdbcTemplate.queryForList(
                "SELECT active_seq FROM resource_edit_pointer WHERE resource_type=? AND resource_id=?",
                Long.class, resourceType, resourceId);
        return list.isEmpty() ? null : list.getFirst();
    }

    public void setActiveSeq(String resourceType, String resourceId, long activeSeq) {
        Long existing = getActiveSeq(resourceType, resourceId);
        long now = System.currentTimeMillis();
        if (existing == null) {
            jdbcTemplate.update(
                    "INSERT INTO resource_edit_pointer (resource_type, resource_id, active_seq, update_time) VALUES (?,?,?,?)",
                    resourceType, resourceId, activeSeq, now);
        } else {
            jdbcTemplate.update(
                    "UPDATE resource_edit_pointer SET active_seq=?, update_time=? WHERE resource_type=? AND resource_id=?",
                    activeSeq, now, resourceType, resourceId);
        }
    }
}
