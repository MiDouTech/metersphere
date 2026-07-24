package io.metersphere.system.edit.dao;

import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ResourceEditLockDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> find(String resourceType, String resourceId) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT * FROM resource_edit_lock WHERE resource_type = ? AND resource_id = ?",
                resourceType, resourceId);
        return list.isEmpty() ? null : list.getFirst();
    }

    public void upsert(String resourceType, String resourceId, String projectId,
                       String holderUserId, String holderUserName, long expireTime) {
        Map<String, Object> existing = find(resourceType, resourceId);
        long now = System.currentTimeMillis();
        if (existing == null) {
            jdbcTemplate.update(
                    "INSERT INTO resource_edit_lock (id, resource_type, resource_id, project_id, holder_user_id, holder_user_name, expire_time, create_time, update_time) VALUES (?,?,?,?,?,?,?,?,?)",
                    IDGenerator.nextStr(), resourceType, resourceId, projectId,
                    holderUserId, holderUserName, expireTime, now, now);
        } else {
            jdbcTemplate.update(
                    "UPDATE resource_edit_lock SET holder_user_id=?, holder_user_name=?, expire_time=?, update_time=?, project_id=? WHERE resource_type=? AND resource_id=?",
                    holderUserId, holderUserName, expireTime, now, projectId, resourceType, resourceId);
        }
    }

    public int delete(String resourceType, String resourceId, String holderUserId) {
        if (StringUtils.isBlank(holderUserId)) {
            return jdbcTemplate.update(
                    "DELETE FROM resource_edit_lock WHERE resource_type=? AND resource_id=?",
                    resourceType, resourceId);
        }
        return jdbcTemplate.update(
                "DELETE FROM resource_edit_lock WHERE resource_type=? AND resource_id=? AND holder_user_id=?",
                resourceType, resourceId, holderUserId);
    }

    public void deleteExpired(long now) {
        jdbcTemplate.update("DELETE FROM resource_edit_lock WHERE expire_time < ?", now);
    }
}
