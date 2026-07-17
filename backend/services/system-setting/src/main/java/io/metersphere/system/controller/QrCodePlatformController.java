package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.DingTalkInfoDTO;
import io.metersphere.system.dto.LarkInfoDTO;
import io.metersphere.system.dto.WeComInfoDTO;
import io.metersphere.system.dto.request.EnableEditorRequest;
import io.metersphere.system.service.PlatformSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

/**
 * 社区版扫码登录配置接口（路径与前端 / 企业版 xpack 保持一致）
 */
@RestController
@Tag(name = "系统设置-扫码登录")
public class QrCodePlatformController {

    @Resource
    private PlatformSourceService platformSourceService;

    @GetMapping("/we_com/info/with_detail")
    @Operation(summary = "获取企业微信扫码配置详情")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_READ)
    public WeComInfoDTO getWeComDetail() {
        return platformSourceService.getWeComDetail();
    }

    @PostMapping("/we_com/save")
    @Operation(summary = "保存企业微信扫码配置")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void saveWeCom(@RequestBody WeComInfoDTO request) {
        platformSourceService.saveWeCom(request);
    }

    @PostMapping("/we_com/validate")
    @Operation(summary = "校验企业微信扫码配置")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void validateWeCom(@RequestBody WeComInfoDTO request) {
        platformSourceService.validateWeCom(request);
    }

    @PostMapping("/we_com/enable")
    @Operation(summary = "启用/关闭企业微信扫码登录")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void enableWeCom(@RequestBody EnableEditorRequest request) {
        platformSourceService.enable(PlatformSourceService.WE_COM, request);
    }

    @PostMapping("/we_com/change/validate")
    @Operation(summary = "标记企业微信校验失败")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void invalidateWeCom() {
        platformSourceService.invalidate(PlatformSourceService.WE_COM);
    }

    @GetMapping("/ding_talk/info/with_detail")
    @Operation(summary = "获取钉钉扫码配置详情")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_READ)
    public DingTalkInfoDTO getDingTalkDetail() {
        return platformSourceService.getDingTalkDetail();
    }

    @PostMapping("/ding_talk/save")
    @Operation(summary = "保存钉钉扫码配置")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void saveDingTalk(@RequestBody DingTalkInfoDTO request) {
        platformSourceService.saveDingTalk(request);
    }

    @PostMapping("/ding_talk/validate")
    @Operation(summary = "校验钉钉扫码配置")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void validateDingTalk(@RequestBody DingTalkInfoDTO request) {
        platformSourceService.validateDingTalk(request);
    }

    @PostMapping("/ding_talk/enable")
    @Operation(summary = "启用/关闭钉钉扫码登录")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void enableDingTalk(@RequestBody EnableEditorRequest request) {
        platformSourceService.enable(PlatformSourceService.DING_TALK, request);
    }

    @PostMapping("/ding_talk/change/validate")
    @Operation(summary = "标记钉钉校验失败")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void invalidateDingTalk() {
        platformSourceService.invalidate(PlatformSourceService.DING_TALK);
    }

    @GetMapping("/lark/info/with_detail")
    @Operation(summary = "获取飞书扫码配置详情")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_READ)
    public LarkInfoDTO getLarkDetail() {
        return platformSourceService.getLarkDetail();
    }

    @PostMapping("/lark/save")
    @Operation(summary = "保存飞书扫码配置")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void saveLark(@RequestBody LarkInfoDTO request) {
        platformSourceService.saveLark(request);
    }

    @PostMapping("/lark/validate")
    @Operation(summary = "校验飞书扫码配置")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void validateLark(@RequestBody LarkInfoDTO request) {
        platformSourceService.validateLark(request, PlatformSourceService.LARK);
    }

    @PostMapping("/lark/enable")
    @Operation(summary = "启用/关闭飞书扫码登录")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void enableLark(@RequestBody EnableEditorRequest request) {
        platformSourceService.enable(PlatformSourceService.LARK, request);
    }

    @PostMapping("/lark/change/validate")
    @Operation(summary = "标记飞书校验失败")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void invalidateLark() {
        platformSourceService.invalidate(PlatformSourceService.LARK);
    }

    @GetMapping("/lark_suite/info/with_detail")
    @Operation(summary = "获取国际飞书扫码配置详情")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_READ)
    public LarkInfoDTO getLarkSuiteDetail() {
        return platformSourceService.getLarkSuiteDetail();
    }

    @PostMapping("/lark_suite/save")
    @Operation(summary = "保存国际飞书扫码配置")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void saveLarkSuite(@RequestBody LarkInfoDTO request) {
        platformSourceService.saveLarkSuite(request);
    }

    @PostMapping("/lark_suite/validate")
    @Operation(summary = "校验国际飞书扫码配置")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void validateLarkSuite(@RequestBody LarkInfoDTO request) {
        platformSourceService.validateLark(request, PlatformSourceService.LARK_SUITE);
    }

    @PostMapping("/lark_suite/enable")
    @Operation(summary = "启用/关闭国际飞书扫码登录")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void enableLarkSuite(@RequestBody EnableEditorRequest request) {
        platformSourceService.enable(PlatformSourceService.LARK_SUITE, request);
    }

    @PostMapping("/lark_suite/change/validate")
    @Operation(summary = "标记国际飞书校验失败")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_QRCODE_UPDATE)
    public void invalidateLarkSuite() {
        platformSourceService.invalidate(PlatformSourceService.LARK_SUITE);
    }
}
