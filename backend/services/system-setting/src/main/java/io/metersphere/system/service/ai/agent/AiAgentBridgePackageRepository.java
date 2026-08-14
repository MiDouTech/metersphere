package io.metersphere.system.service.ai.agent;

import io.metersphere.system.dto.ai.agent.AiAgentBridgePackageDTO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class AiAgentBridgePackageRepository {
    private final JdbcTemplate jdbcTemplate;

    public AiAgentBridgePackageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AiAgentBridgePackageDTO> list() {
        return jdbcTemplate.query("""
                SELECT * FROM ai_agent_bridge_package
                ORDER BY update_time DESC, create_time DESC
                """, this::map);
    }

    public AiAgentBridgePackageDTO findById(String id) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM ai_agent_bridge_package WHERE id=?", this::map, id);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    public AiAgentBridgePackageDTO findActive(String osType, String architecture) {
        List<AiAgentBridgePackageDTO> rows = jdbcTemplate.query("""
                SELECT * FROM ai_agent_bridge_package
                WHERE os_type=? AND architecture=? AND status='ACTIVE'
                ORDER BY update_time DESC LIMIT 1
                """, this::map, osType, architecture);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void insert(AiAgentBridgePackageDTO value) {
        jdbcTemplate.update("""
                INSERT INTO ai_agent_bridge_package
                (id, version, os_type, architecture, file_name, storage, storage_folder, sha256,
                 size_bytes, status, active_key, description, download_count, create_user, create_time, update_user, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
                """, value.getId(), value.getVersion(), value.getOsType(), value.getArchitecture(),
                value.getFileName(), value.getStorage(), value.getStorageFolder(), value.getSha256(),
                value.getSizeBytes(), value.getStatus(), "ACTIVE".equals(value.getStatus()) ? activeKey(value) : null,
                value.getDescription(), value.getCreateUser(),
                value.getCreateTime(), value.getUpdateUser(), value.getUpdateTime());
    }

    public void deactivatePlatform(String osType, String architecture, String userId, long now) {
        jdbcTemplate.update("""
                UPDATE ai_agent_bridge_package SET status='INACTIVE', active_key=NULL, update_user=?, update_time=?
                WHERE os_type=? AND architecture=? AND status='ACTIVE'
                """, userId, now, osType, architecture);
    }

    public int updateStatus(String id, String status, String userId, long now) {
        return jdbcTemplate.update("""
                UPDATE ai_agent_bridge_package SET status=?, active_key=CASE WHEN ?='ACTIVE' THEN CONCAT(os_type, ':', architecture) ELSE NULL END,
                       update_user=?, update_time=? WHERE id=?
                """, status, status, userId, now, id);
    }

    public int delete(String id) {
        return jdbcTemplate.update("DELETE FROM ai_agent_bridge_package WHERE id=? AND status='INACTIVE'", id);
    }

    public void incrementDownloadCount(String id) {
        jdbcTemplate.update("""
                UPDATE ai_agent_bridge_package
                SET download_count=download_count+1 WHERE id=?
                """, id);
    }

    private AiAgentBridgePackageDTO map(ResultSet rs, int rowNum) throws SQLException {
        AiAgentBridgePackageDTO value = new AiAgentBridgePackageDTO();
        value.setId(rs.getString("id"));
        value.setVersion(rs.getString("version"));
        value.setOsType(rs.getString("os_type"));
        value.setArchitecture(rs.getString("architecture"));
        value.setFileName(rs.getString("file_name"));
        value.setStorage(rs.getString("storage"));
        value.setStorageFolder(rs.getString("storage_folder"));
        value.setSha256(rs.getString("sha256"));
        value.setSizeBytes(rs.getLong("size_bytes"));
        value.setStatus(rs.getString("status"));
        value.setDescription(rs.getString("description"));
        value.setDownloadCount(rs.getLong("download_count"));
        value.setCreateUser(rs.getString("create_user"));
        value.setCreateTime(rs.getLong("create_time"));
        value.setUpdateUser(rs.getString("update_user"));
        value.setUpdateTime(rs.getLong("update_time"));
        return value;
    }

    private String activeKey(AiAgentBridgePackageDTO value) {
        return value.getOsType() + ":" + value.getArchitecture();
    }
}
