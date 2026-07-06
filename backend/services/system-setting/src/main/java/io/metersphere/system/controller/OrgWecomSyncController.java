package io.metersphere.system.controller;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.domain.OrgSyncLog;
import io.metersphere.system.dto.department.OrgWecomSyncConfigDTO;
import io.metersphere.system.dto.department.OrgWecomSyncConfigSaveRequest;
import io.metersphere.system.dto.department.OrgWecomSyncConfigTestRequest;
import io.metersphere.system.dto.department.OrgWecomSyncConfigTestResponse;
import io.metersphere.system.dto.department.OrgWecomSyncLogPageRequest;
import io.metersphere.system.dto.department.OrgWecomSyncManualResponse;
import io.metersphere.system.dto.department.OrgWecomSyncStatusDTO;
import io.metersphere.system.dto.department.SyncResult;
import io.metersphere.system.service.department.OrgWecomSyncAccessService;
import io.metersphere.system.service.department.OrgWecomSyncConfigService;
import io.metersphere.system.service.department.OrgWecomSyncQueryService;
import io.metersphere.system.service.department.WecomOrgSyncApplicationService;
import io.metersphere.system.utils.PageUtils;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.utils.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "组织架构-企微同步")
@RestController
@RequestMapping("/org-wecom")
@Validated
public class OrgWecomSyncController {

    @Resource
    private WecomOrgSyncApplicationService wecomOrgSyncApplicationService;
    @Resource
    private OrgWecomSyncQueryService orgWecomSyncQueryService;
    @Resource
    private OrgWecomSyncConfigService orgWecomSyncConfigService;
    @Resource
    private OrgWecomSyncAccessService orgWecomSyncAccessService;

    @PostMapping("/sync/manual")
    @Operation(summary = "组织架构-手动同步企微通讯录")
    @RequiresPermissions(value = {
            PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ_UPDATE,
            PermissionConstants.ORGANIZATION_MEMBER_UPDATE
    }, logical = Logical.OR)
    public OrgWecomSyncManualResponse manualSync(@RequestParam @NotBlank String organizationId) {
        orgWecomSyncAccessService.validateWritable(organizationId);
        SessionUtils.setCurrentOrganizationId(organizationId);
        try {
            SyncResult result = wecomOrgSyncApplicationService.syncManual(organizationId, SessionUtils.getUserId());
            return toManualResponse(result);
        } finally {
            SessionUtils.clearCurrentOrganizationId();
        }
    }

    @GetMapping("/sync/status")
    @Operation(summary = "组织架构-最近同步状态")
    @RequiresPermissions(value = {
            PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ,
            PermissionConstants.ORGANIZATION_MEMBER_READ
    }, logical = Logical.OR)
    public OrgWecomSyncStatusDTO status(@RequestParam @NotBlank String organizationId) {
        SessionUtils.setCurrentOrganizationId(organizationId);
        try {
            return orgWecomSyncQueryService.getStatus(organizationId);
        } finally {
            SessionUtils.clearCurrentOrganizationId();
        }
    }

    @GetMapping("/sync/log/page")
    @Operation(summary = "组织架构-同步日志分页")
    @RequiresPermissions(value = {
            PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ,
            PermissionConstants.ORGANIZATION_MEMBER_READ
    }, logical = Logical.OR)
    public Pager<List<OrgSyncLog>> logPage(@Validated OrgWecomSyncLogPageRequest request) {
        SessionUtils.setCurrentOrganizationId(request.getOrganizationId());
        try {
            Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
            return PageUtils.setPageInfo(page, orgWecomSyncQueryService.pageLogs(request));
        } finally {
            SessionUtils.clearCurrentOrganizationId();
        }
    }

    @GetMapping("/config/get")
    @Operation(summary = "组织架构-获取企微同步配置")
    @RequiresPermissions(value = {
            PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ,
            PermissionConstants.ORGANIZATION_MEMBER_READ
    }, logical = Logical.OR)
    public OrgWecomSyncConfigDTO getConfig(@RequestParam @NotBlank String organizationId) {
        SessionUtils.setCurrentOrganizationId(organizationId);
        try {
            return orgWecomSyncConfigService.get(organizationId);
        } finally {
            SessionUtils.clearCurrentOrganizationId();
        }
    }

    @PostMapping("/config/test")
    @Operation(summary = "组织架构-测试企微通讯录连接")
    @RequiresPermissions(value = {
            PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ_UPDATE,
            PermissionConstants.ORGANIZATION_MEMBER_UPDATE
    }, logical = Logical.OR)
    public OrgWecomSyncConfigTestResponse testConfig(@Valid @RequestBody OrgWecomSyncConfigTestRequest request) {
        SessionUtils.setCurrentOrganizationId(request.getOrganizationId());
        try {
            return orgWecomSyncConfigService.test(request, SessionUtils.getUserId());
        } finally {
            SessionUtils.clearCurrentOrganizationId();
        }
    }

    @PostMapping("/config/save")
    @Operation(summary = "组织架构-保存企微同步配置")
    @RequiresPermissions(value = {
            PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ_UPDATE,
            PermissionConstants.ORGANIZATION_MEMBER_UPDATE
    }, logical = Logical.OR)
    public void saveConfig(@Valid @RequestBody OrgWecomSyncConfigSaveRequest request) {
        SessionUtils.setCurrentOrganizationId(request.getOrganizationId());
        try {
            orgWecomSyncConfigService.save(request, SessionUtils.getUserId());
        } finally {
            SessionUtils.clearCurrentOrganizationId();
        }
    }

    private OrgWecomSyncManualResponse toManualResponse(SyncResult result) {
        OrgWecomSyncManualResponse response = new OrgWecomSyncManualResponse();
        response.setSyncLogId(result.getSyncLogId());
        response.setSyncStatus(result.getSyncStatus());
        response.setDeptSuccess(result.getDeptSuccess());
        response.setDeptFailed(result.getDeptFailed());
        response.setUserSuccess(result.getUserSuccess());
        response.setUserFailed(result.getUserFailed());
        response.setDurationMs(result.getDurationMs());
        response.setErrorMessage(result.getErrorMessage());
        return response;
    }
}
