package io.metersphere.plan.hub.dao;

import io.metersphere.plan.hub.dto.DefaultHubPlanMapRow;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DefaultHubPlanMapDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public DefaultHubPlanMapRow findByBizPlanId(String bizPlanId) {
        List<DefaultHubPlanMapRow> rows = jdbcTemplate.query(
                "SELECT id, biz_project_id AS bizProjectId, biz_plan_id AS bizPlanId, hub_plan_id AS hubPlanId, " +
                        "content_hash AS contentHash, create_time AS createTime, update_time AS updateTime " +
                        "FROM default_hub_plan_map WHERE biz_plan_id = ?",
                new BeanPropertyRowMapper<>(DefaultHubPlanMapRow.class), bizPlanId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void insert(String bizProjectId, String bizPlanId, String hubPlanId, String contentHash) {
        long now = System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO default_hub_plan_map (id, biz_project_id, biz_plan_id, hub_plan_id, content_hash, create_time, update_time) " +
                        "VALUES (?,?,?,?,?,?,?)",
                IDGenerator.nextStr(), bizProjectId, bizPlanId, hubPlanId, contentHash, now, now);
    }

    public void updateHash(String bizPlanId, String contentHash) {
        jdbcTemplate.update(
                "UPDATE default_hub_plan_map SET content_hash = ?, update_time = ? WHERE biz_plan_id = ?",
                contentHash, System.currentTimeMillis(), bizPlanId);
    }

    public void deleteByBizPlanId(String bizPlanId) {
        jdbcTemplate.update("DELETE FROM default_hub_plan_map WHERE biz_plan_id = ?", bizPlanId);
    }

    public List<DefaultHubPlanMapRow> listByBizProjectId(String bizProjectId) {
        return jdbcTemplate.query(
                "SELECT id, biz_project_id AS bizProjectId, biz_plan_id AS bizPlanId, hub_plan_id AS hubPlanId, " +
                        "content_hash AS contentHash, create_time AS createTime, update_time AS updateTime " +
                        "FROM default_hub_plan_map WHERE biz_project_id = ?",
                new BeanPropertyRowMapper<>(DefaultHubPlanMapRow.class), bizProjectId);
    }
}
