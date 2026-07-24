package io.metersphere.functional.hub.dao;

import io.metersphere.functional.hub.dto.DefaultHubSyncJobRow;
import io.metersphere.sdk.constants.DefaultHubConstants;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * default_hub_sync_job JDBC 访问
 */
@Repository
public class DefaultHubSyncJobDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public String createJob(String jobType, String scopeProjectId, String createUser) {
        String id = IDGenerator.nextStr();
        long now = System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO default_hub_sync_job (id, job_type, scope_project_id, status, progress, success_count, fail_count, create_user, create_time, update_time) " +
                        "VALUES (?,?,?,?,0,0,0,?,?,?)",
                id, jobType, scopeProjectId, DefaultHubConstants.JOB_STATUS_PENDING, createUser, now, now);
        return id;
    }

    public DefaultHubSyncJobRow findById(String jobId) {
        List<DefaultHubSyncJobRow> rows = jdbcTemplate.query(
                "SELECT id, job_type AS jobType, scope_project_id AS scopeProjectId, status, progress, " +
                        "success_count AS successCount, fail_count AS failCount, error_message AS errorMessage, " +
                        "create_user AS createUser, create_time AS createTime, update_time AS updateTime, finish_time AS finishTime " +
                        "FROM default_hub_sync_job WHERE id = ?",
                new BeanPropertyRowMapper<>(DefaultHubSyncJobRow.class), jobId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 多节点简易抢锁：PENDING → RUNNING */
    public boolean tryAcquire(String jobId) {
        int updated = jdbcTemplate.update(
                "UPDATE default_hub_sync_job SET status = ?, update_time = ? WHERE id = ? AND status = ?",
                DefaultHubConstants.JOB_STATUS_RUNNING, System.currentTimeMillis(), jobId, DefaultHubConstants.JOB_STATUS_PENDING);
        return updated > 0;
    }

    public void updateProgress(String jobId, int progress, int successCount, int failCount) {
        jdbcTemplate.update(
                "UPDATE default_hub_sync_job SET progress = ?, success_count = ?, fail_count = ?, update_time = ? WHERE id = ?",
                progress, successCount, failCount, System.currentTimeMillis(), jobId);
    }

    public void finish(String jobId, String status, int progress, int successCount, int failCount, String errorMessage) {
        long now = System.currentTimeMillis();
        jdbcTemplate.update(
                "UPDATE default_hub_sync_job SET status = ?, progress = ?, success_count = ?, fail_count = ?, " +
                        "error_message = ?, update_time = ?, finish_time = ? WHERE id = ?",
                status, progress, successCount, failCount,
                StringUtils.left(errorMessage, 2000), now, now, jobId);
    }
}
