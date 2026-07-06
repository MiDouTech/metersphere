package io.metersphere.system.service.department;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import io.metersphere.system.dto.user.UserDTO;
import io.metersphere.system.mapper.ExtCheckOwnerMapper;
import io.metersphere.system.service.PermissionCheckService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrgWecomSyncAccessService {

    @Resource
    private PermissionCheckService permissionCheckService;
    @Resource
    private ExtCheckOwnerMapper extCheckOwnerMapper;

    public void validateReadable(String organizationId) {
        validateOrganizationAccess(organizationId);
    }

    public void validateWritable(String organizationId) {
        validateOrganizationAccess(organizationId);
    }

    private void validateOrganizationAccess(String organizationId) {
        if (StringUtils.isBlank(organizationId)) {
            throw new MSException("organization_id is required");
        }
        UserDTO user = permissionCheckService.getUserDTO(SessionUtils.getUserId());
        if (user == null) {
            throw new MSException(MsHttpResultCode.FORBIDDEN);
        }
        if (permissionCheckService.checkAdmin(user)) {
            return;
        }
        if (!extCheckOwnerMapper.checkoutOrganization(SessionUtils.getUserId(), List.of(organizationId))) {
            throw new MSException(MsHttpResultCode.FORBIDDEN);
        }
        if (!StringUtils.equals(organizationId, SessionUtils.getCurrentOrganizationId())) {
            throw new MSException(MsHttpResultCode.FORBIDDEN);
        }
    }
}
