package io.metersphere.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 补齐 Shiro anon 的 /system/theme，避免 No static resource。
 */
@RestController
@RequestMapping("/system")
@Tag(name = "系统设置-主题")
public class SystemThemeController {

    @GetMapping("/theme")
    @Operation(summary = "获取系统主题（社区默认）")
    public Map<String, String> theme() {
        return Map.of("theme", "default");
    }
}
