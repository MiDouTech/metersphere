package io.metersphere.system.service.department;

import io.metersphere.system.domain.OrgSyncLog;
import io.metersphere.system.domain.OrgWecomSyncConfig;
import io.metersphere.system.domain.OrgWecomSyncConfigExample;
import io.metersphere.system.dto.department.OrgWecomSyncLogPageRequest;
import io.metersphere.system.dto.department.OrgWecomSyncStatusDTO;
import io.metersphere.system.mapper.ExtOrgSyncLogMapper;
import io.metersphere.system.mapper.OrgWecomSyncConfigMapper;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrgWecomSyncQueryService {

    @Resource
    private OrgWecomSyncAccessService orgWecomSyncAccessService;
    @Resource
    private ExtOrgSyncLogMapper extOrgSyncLogMapper;
    @Resource
    private OrgWecomSyncConfigMapper orgWecomSyncConfigMapper;

    public OrgWecomSyncStatusDTO getStatus(String organizationId) {
        orgWecomSyncAccessService.validateReadable(organizationId);
        OrgWecomSyncStatusDTO status = new OrgWecomSyncStatusDTO();
        status.setOrganizationId(organizationId);

        OrgWecomSyncConfigExample example = new OrgWecomSyncConfigExample();
        example.createCriteria().andOrganizationIdEqualTo(organizationId);
        List<OrgWecomSyncConfig> configs = orgWecomSyncConfigMapper.selectByExample(example);
        if (CollectionUtils.isNotEmpty(configs)) {
            status.setLastSyncTime(configs.getFirst().getLastSyncTime());
        }

        OrgSyncLog latestLog = extOrgSyncLogMapper.selectLatestByOrganizationId(organizationId);
        if (latestLog != null) {
            status.setSyncLogId(latestLog.getId());
            status.setSyncMode(latestLog.getSyncMode());
            status.setSyncStatus(latestLog.getSyncStatus());
            status.setDeptTotal(latestLog.getDeptTotal());
            status.setDeptSuccess(latestLog.getDeptSuccess());
            status.setDeptFailed(latestLog.getDeptFailed());
            status.setUserTotal(latestLog.getUserTotal());
            status.setUserSuccess(latestLog.getUserSuccess());
            status.setUserFailed(latestLog.getUserFailed());
            status.setDurationMs(latestLog.getDurationMs());
            status.setErrorMessage(latestLog.getErrorMessage());
            status.setLogCreateTime(latestLog.getCreateTime());
        }
        return status;
    }

    public List<OrgSyncLog> pageLogs(OrgWecomSyncLogPageRequest request) {
        orgWecomSyncAccessService.validateReadable(request.getOrganizationId());
        return extOrgSyncLogMapper.listByOrganizationId(request.getOrganizationId(), request.getSyncStatus());
    }
}
