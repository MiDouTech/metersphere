package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.DisplayDTO;
import io.metersphere.system.service.DisplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 社区版界面配置：补齐企业版 xpack /display/*，避免 Spring 落到静态资源报
 * 「No static resource display/info.」
 */
@RestController
@RequestMapping("/display")
@Tag(name = "系统设置-界面配置")
public class DisplayController {

    @Resource
    private DisplayService displayService;

    @GetMapping("/info")
    @Operation(summary = "获取界面配置（登录页/系统页 anon 可访问）")
    public List<DisplayDTO> info() {
        return displayService.uiInfo();
    }

    @PostMapping(value = "/save", consumes = {"multipart/form-data", "application/json"})
    @Operation(summary = "保存界面配置")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_DISPLAY_READ_UPDATE)
    public void save(@RequestPart(value = "request", required = false) String requestJson,
                     @RequestPart(value = "files", required = false) List<MultipartFile> files,
                     @RequestBody(required = false) List<DisplayDTO> body) {
        List<DisplayDTO> requests = body != null ? body : displayService.parseRequest(requestJson);
        displayService.save(requests, files);
    }
}
