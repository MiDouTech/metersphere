package io.metersphere.system.service.department;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import io.metersphere.system.domain.OrgSyncLog;
import io.metersphere.system.domain.OrgWecomSyncConfig;
import io.metersphere.system.dto.department.SyncResult;
import io.metersphere.system.mapper.OrgSyncLogMapper;
import io.metersphere.system.mapper.OrgWecomSyncConfigMapper;
import io.metersphere.system.service.wecom.WecomContactConfigProvider;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class WecomOrgSyncApplicationService {

    private static final String LOCK_PREFIX = "ms:wecom:org-sync:";
    private static final long LOCK_TTL_SECONDS = 1800;

    @Resource
    private WecomOrgSyncService wecomOrgSyncService;
    @Resource
    private WecomContactConfigProvider wecomContactConfigProvider;
    @Resource
    private OrgSyncLogMapper orgSyncLogMapper;
    @Resource
    private OrgWecomSyncConfigMapper orgWecomSyncConfigMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Transactional(rollbackFor = Exception.class)
    public SyncResult syncManual(String organizationId, String operatorId) {
        return executeWithLock(organizationId, operatorId, OrgSyncConstants.SYNC_MODE_MANUAL);
    }

    @Transactional(rollbackFor = Exception.class)
    public SyncResult syncSchedule(String organizationId) {
        return executeWithLock(organizationId, "system", OrgSyncConstants.SYNC_MODE_SCHEDULE);
    }

    private SyncResult executeWithLock(String organizationId, String operatorId, String syncMode) {
        String lockKey = LOCK_PREFIX + organizationId;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, operatorId, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            throw new MSException(MsHttpResultCode.CONFLICT,
                    "organization wecom sync is already running: " + organizationId);
        }
        try {
            return doSync(organizationId, operatorId, syncMode);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private SyncResult doSync(String organizationId, String operatorId, String syncMode) {
        long startTime = System.currentTimeMillis();
        OrgWecomSyncConfig config = wecomContactConfigProvider.getByOrganizationId(organizationId);
        try {
            SyncResult result = wecomOrgSyncService.syncAll(organizationId, operatorId);
            result.setSyncMode(syncMode);
            String syncLogId = saveSyncLog(organizationId, operatorId, syncMode, result);
            updateConfigLastSyncTime(config, operatorId);
            result.setSyncLogId(syncLogId);
            return result;
        } catch (Exception ex) {
            SyncResult failed = buildFailedResult(organizationId, syncMode, startTime, ex.getMessage());
            saveSyncLog(organizationId, operatorId, syncMode, failed);
            throw new MSException("wecom sync failed: " + ex.getMessage());
        }
    }

    private String saveSyncLog(String organizationId, String operatorId, String syncMode, SyncResult result) {
        OrgSyncLog log = new OrgSyncLog();
        String logId = IDGenerator.nextStr();
        log.setId(logId);
        log.setOrganizationId(organizationId);
        log.setSyncMode(syncMode);
        log.setSyncStatus(StringUtils.defaultIfBlank(result.getSyncStatus(), OrgSyncConstants.SYNC_STATUS_FAILED));
        log.setDeptTotal(defaultZero(result.getDeptTotal()));
        log.setDeptSuccess(defaultZero(result.getDeptSuccess()));
        log.setDeptFailed(defaultZero(result.getDeptFailed()));
        log.setUserTotal(defaultZero(result.getUserTotal()));
        log.setUserSuccess(defaultZero(result.getUserSuccess()));
        log.setUserFailed(defaultZero(result.getUserFailed()));
        log.setDurationMs(result.getDurationMs() == null ? 0L : result.getDurationMs());
        log.setErrorMessage(result.getErrorMessage());
        log.setCreateTime(System.currentTimeMillis());
        log.setCreateUser(operatorId);
        orgSyncLogMapper.insert(log);
        return logId;
    }

    private void updateConfigLastSyncTime(OrgWecomSyncConfig config, String operatorId) {
        OrgWecomSyncConfig update = new OrgWecomSyncConfig();
        update.setId(config.getId());
        update.setLastSyncTime(System.currentTimeMillis());
        update.setUpdateTime(System.currentTimeMillis());
        update.setUpdateUser(operatorId);
        orgWecomSyncConfigMapper.updateByPrimaryKeySelective(update);
    }

    private SyncResult buildFailedResult(String organizationId, String syncMode, long startTime, String errorMessage) {
        SyncResult result = new SyncResult();
        result.setOrganizationId(organizationId);
        result.setSyncMode(syncMode);
        result.setSyncStatus(OrgSyncConstants.SYNC_STATUS_FAILED);
        result.setDeptTotal(0);
        result.setDeptSuccess(0);
        result.setDeptFailed(0);
        result.setUserTotal(0);
        result.setUserSuccess(0);
        result.setUserFailed(0);
        result.setDurationMs(System.currentTimeMillis() - startTime);
        result.setErrorMessage(errorMessage);
        return result;
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }
}
