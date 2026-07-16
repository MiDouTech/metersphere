package io.metersphere.system.controller;

import io.metersphere.system.dto.AuthSourceDTO;
import io.metersphere.system.service.AuthSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录页拉取已启用认证源（Shiro anon /authsource/list/allenable）
 */
@RestController
@RequestMapping("/authsource")
@Tag(name = "认证源-公开接口")
public class AuthSourcePublicController {

    @Resource
    private AuthSourceService authSourceService;

    @GetMapping("/list/allenable")
    @Operation(summary = "已启用认证源列表")
    public List<AuthSourceDTO> listAllEnable() {
        return authSourceService.listEnabled();
    }
}
