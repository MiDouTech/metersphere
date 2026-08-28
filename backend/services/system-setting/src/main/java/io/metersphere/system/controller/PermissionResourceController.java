package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.permission.PermissionResourceDTO;
import io.metersphere.system.service.PermissionUiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "权限资源")
@RestController
@RequestMapping("/permission")
public class PermissionResourceController {

    @Resource
    private PermissionUiService permissionUiService;

    @GetMapping("/resource/tree")
    @Operation(summary = "查询 UI 权限资源树")
    @RequiresPermissions(value = {
            PermissionConstants.SYSTEM_USER_ROLE_READ,
            PermissionConstants.ORGANIZATION_USER_ROLE_READ,
            PermissionConstants.PROJECT_GROUP_READ,
            PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ
    }, logical = Logical.OR)
    public List<PermissionResourceDTO> tree(@Parameter(description = "SYSTEM / ORGANIZATION / PROJECT")
                                            @RequestParam String scopeType) {
        return permissionUiService.getResourceTree(scopeType);
    }

}
