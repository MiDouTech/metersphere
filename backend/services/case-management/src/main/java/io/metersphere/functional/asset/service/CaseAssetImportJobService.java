package io.metersphere.functional.asset.service;

import io.metersphere.functional.dto.response.FunctionalCaseImportResponse;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import io.metersphere.sdk.exception.MSException;

import java.util.List;
import java.util.Map;

@Service
public class CaseAssetImportJobService {
    @Resource
    private JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        try {
            jdbcTemplate.update("UPDATE case_asset_import_job SET status='FAILED',fail_count=GREATEST(fail_count,1),"
                            + "error_detail=COALESCE(NULLIF(error_detail,''),?),update_time=? WHERE status='RUNNING'",
                    "服务重启导致导入中断，请重新选择文件导入", System.currentTimeMillis());
        } catch (Exception e) {
            LogUtils.error("Recover interrupted case asset import jobs failed", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String create(String catalogId, MultipartFile file, String strategy) {
        String id = IDGenerator.nextStr();
        long now = System.currentTimeMillis();
        jdbcTemplate.update("INSERT INTO case_asset_import_job (id, catalog_id, file_name, conflict_strategy, status, "
                        + "create_user, create_time, update_time) VALUES (?, ?, ?, ?, 'RUNNING', ?, ?, ?)",
                id, catalogId, file == null ? null : file.getOriginalFilename(), strategy,
                SessionUtils.getUserId(), now, now);
        return id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String id, FunctionalCaseImportResponse response) {
        int success = response == null ? 0 : response.getSuccessCount();
        int fail = response == null ? 0 : response.getFailCount();
        String detail = response == null || response.getErrorMessages() == null
                ? null : StringUtils.left(JSON.toJSONString(response.getErrorMessages()), 65535);
        jdbcTemplate.update("UPDATE case_asset_import_job SET status = ?, total_count = ?, success_count = ?, "
                        + "fail_count = ?, error_detail = ?, update_time = ? WHERE id = ?",
                fail > 0 ? "PARTIAL_SUCCESS" : "SUCCESS", success + fail, success, fail, detail,
                System.currentTimeMillis(), id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String id, RuntimeException exception) {
        String errorMessage = StringUtils.defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName());
        jdbcTemplate.update("UPDATE case_asset_import_job SET status = 'FAILED', fail_count = 1, "
                        + "error_detail = ?, update_time = ? WHERE id = ?",
                StringUtils.left(errorMessage, 65535), System.currentTimeMillis(), id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String id, String organizationId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT j.id, j.catalog_id catalogId, j.file_name fileName, "
                        + "j.conflict_strategy conflictStrategy, j.status, j.total_count totalCount, j.success_count successCount, "
                        + "j.fail_count failCount, j.error_detail errorDetail, j.create_user createUser, j.create_time createTime, "
                        + "j.update_time updateTime FROM case_asset_import_job j JOIN case_asset_catalog c ON c.id=j.catalog_id "
                        + "WHERE j.id=? AND c.organization_id=?", id, organizationId);
        if (rows.isEmpty()) throw new MSException("资产导入任务不存在");
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLatest(String catalogId, String organizationId) {
        List<String> ids = jdbcTemplate.queryForList("SELECT j.id FROM case_asset_import_job j "
                        + "JOIN case_asset_catalog c ON c.id=j.catalog_id "
                        + "WHERE j.catalog_id=? AND c.organization_id=? ORDER BY j.create_time DESC LIMIT 1",
                String.class, catalogId, organizationId);
        if (ids.isEmpty()) return Map.of("exists", false);
        Map<String, Object> result = new java.util.LinkedHashMap<>(get(ids.getFirst(), organizationId));
        result.put("exists", true);
        return result;
    }
}
