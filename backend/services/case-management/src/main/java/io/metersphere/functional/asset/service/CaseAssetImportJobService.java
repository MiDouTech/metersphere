package io.metersphere.functional.asset.service;

import io.metersphere.functional.dto.response.FunctionalCaseImportResponse;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
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
                ? null : StringUtils.left(response.getErrorMessages().toString(), 65535);
        jdbcTemplate.update("UPDATE case_asset_import_job SET status = ?, total_count = ?, success_count = ?, "
                        + "fail_count = ?, error_detail = ?, update_time = ? WHERE id = ?",
                fail > 0 ? "PARTIAL_SUCCESS" : "SUCCESS", success + fail, success, fail, detail,
                System.currentTimeMillis(), id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String id, RuntimeException exception) {
        jdbcTemplate.update("UPDATE case_asset_import_job SET status = 'FAILED', fail_count = 1, "
                        + "error_detail = ?, update_time = ? WHERE id = ?",
                StringUtils.left(exception.getMessage(), 65535), System.currentTimeMillis(), id);
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
}
