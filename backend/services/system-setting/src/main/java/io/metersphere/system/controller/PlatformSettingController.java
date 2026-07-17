package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.PlatformSourceDTO;
import io.metersphere.system.dto.sdk.OptionDTO;
import io.metersphere.system.service.PlatformSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/setting")
@Tag(name = "系统设置-平台信息")
public class PlatformSettingController {

    @Resource
    private PlatformSourceService platformSourceService;

    @GetMapping("/get/platform/info")
    @Operation(summary = "获取扫码登录平台基础信息列表")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_READ)
    public List<PlatformSourceDTO> getPlatformInfo() {
        return platformSourceService.listPlatformInfo();
    }

    @GetMapping("/get/platform/param")
    @Operation(summary = "获取已启用的扫码登录平台（登录页用）")
    public List<OptionDTO> getPlatformParam() {
        return platformSourceService.listEnabledPlatformOptions();
    }
}
