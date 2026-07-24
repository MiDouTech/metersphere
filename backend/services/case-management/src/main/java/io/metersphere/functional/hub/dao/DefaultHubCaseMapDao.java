package io.metersphere.functional.hub.dao;

import io.metersphere.functional.hub.dto.DefaultHubCaseMapRow;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * default_hub_case_map JDBC 访问
 */
@Repository
public class DefaultHubCaseMapDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public DefaultHubCaseMapRow findByBizCaseId(String bizCaseId) {
        List<DefaultHubCaseMapRow> rows = jdbcTemplate.query(
                "SELECT id, biz_project_id AS bizProjectId, biz_case_id AS bizCaseId, hub_case_id AS hubCaseId, " +
                        "content_hash AS contentHash, create_time AS createTime, update_time AS updateTime " +
                        "FROM default_hub_case_map WHERE biz_case_id = ?",
                new BeanPropertyRowMapper<>(DefaultHubCaseMapRow.class), bizCaseId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void insert(String bizProjectId, String bizCaseId, String hubCaseId, String contentHash) {
        long now = System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO default_hub_case_map (id, biz_project_id, biz_case_id, hub_case_id, content_hash, create_time, update_time) " +
                        "VALUES (?,?,?,?,?,?,?)",
                IDGenerator.nextStr(), bizProjectId, bizCaseId, hubCaseId, contentHash, now, now);
    }

    public void updateHash(String bizCaseId, String contentHash) {
        jdbcTemplate.update(
                "UPDATE default_hub_case_map SET content_hash = ?, update_time = ? WHERE biz_case_id = ?",
                contentHash, System.currentTimeMillis(), bizCaseId);
    }

    public void deleteByBizCaseId(String bizCaseId) {
        jdbcTemplate.update("DELETE FROM default_hub_case_map WHERE biz_case_id = ?", bizCaseId);
    }

    public List<DefaultHubCaseMapRow> listByBizProjectId(String bizProjectId) {
        return jdbcTemplate.query(
                "SELECT id, biz_project_id AS bizProjectId, biz_case_id AS bizCaseId, hub_case_id AS hubCaseId, " +
                        "content_hash AS contentHash, create_time AS createTime, update_time AS updateTime " +
                        "FROM default_hub_case_map WHERE biz_project_id = ?",
                new BeanPropertyRowMapper<>(DefaultHubCaseMapRow.class), bizProjectId);
    }
}
