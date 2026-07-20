package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.DisplayDTO;
import io.metersphere.system.service.DisplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.MediaType;
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

    /**
     * 前端 savePageConfig 固定走 FormData（request + files）。
     * 不可再混用 @RequestBody：multipart 请求时 Spring 会用 JSON 转换器解析 body，
     * 从而报 Content-Type 'multipart/form-data;...;charset=UTF-8' is not supported。
     */
    @PostMapping(value = "/save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "保存界面配置")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_DISPLAY_READ_UPDATE)
    public void save(@RequestPart(value = "request", required = false) String requestJson,
                     @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        displayService.save(displayService.parseRequest(requestJson), files);
    }
}
