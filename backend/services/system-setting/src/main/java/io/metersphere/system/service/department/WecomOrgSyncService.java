package io.metersphere.system.service.department;

import io.metersphere.system.domain.OrgWecomSyncConfig;
import io.metersphere.system.dto.department.SyncResult;
import io.metersphere.system.dto.wecom.WecomDepartmentDTO;
import io.metersphere.system.service.wecom.WecomContactClient;
import io.metersphere.system.service.wecom.WecomContactConfigProvider;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WecomOrgSyncService {

    @Resource
    private WecomContactConfigProvider wecomContactConfigProvider;
    @Resource
    private WecomContactClient wecomContactClient;
    @Resource
    private DepartmentSyncHandler departmentSyncHandler;
    @Resource
    private UserSyncHandler userSyncHandler;

    public SyncResult syncAll(String organizationId, String operatorId) {
        long startTime = System.currentTimeMillis();
        OrgWecomSyncConfig config = wecomContactConfigProvider.getByOrganizationId(organizationId);

        List<WecomDepartmentDTO> departments = wecomContactClient.executeWithToken(
                config.getCorpId(), config.getContactSecret(), wecomContactClient::listDepartments);
        SyncPartResult deptResult = departmentSyncHandler.sync(organizationId, operatorId, departments);
        SyncPartResult userResult = userSyncHandler.sync(organizationId, operatorId,
                config.getCorpId(), config.getContactSecret());

        SyncResult result = new SyncResult();
        result.setOrganizationId(organizationId);
        result.setDeptTotal(deptResult.getTotal());
        result.setDeptSuccess(deptResult.getSuccess());
        result.setDeptFailed(deptResult.getFailed());
        result.setUserTotal(userResult.getTotal());
        result.setUserSuccess(userResult.getSuccess());
        result.setUserFailed(userResult.getFailed());
        result.setDeptCreated(deptResult.getCreated());
        result.setDeptUpdated(deptResult.getUpdated());
        result.setDeptDisabled(deptResult.getDisabled());
        result.setUserCreated(userResult.getCreated());
        result.setUserUpdated(userResult.getUpdated());
        result.setUserDisabled(userResult.getDisabled());
        result.setErrorMessage(joinErrors(deptResult.getErrorMessage(), userResult.getErrorMessage()));
        result.setDurationMs(System.currentTimeMillis() - startTime);
        result.setSyncStatus(resolveSyncStatus(deptResult.getFailed(), userResult.getFailed()));
        return result;
    }

    private String resolveSyncStatus(int deptFailed, int userFailed) {
        if (deptFailed == 0 && userFailed == 0) {
            return OrgSyncConstants.SYNC_STATUS_SUCCESS;
        }
        return OrgSyncConstants.SYNC_STATUS_PARTIAL;
    }

    private String joinErrors(String left, String right) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.isNotBlank(left)) {
            builder.append(left.trim());
        }
        if (StringUtils.isNotBlank(right)) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(right.trim());
        }
        return builder.toString();
    }
}
