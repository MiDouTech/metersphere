package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentModuleAliasDTO;
import io.metersphere.agent.service.AgentModuleAliasService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Agent Config")
@RestController
@RequestMapping("/api/agent/v1/config")
public class AgentModuleAliasController {
    @Resource
    private AgentModuleAliasService agentModuleAliasService;

    @GetMapping("/module-alias")
    @Operation(summary = "模块别名列表")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_READ)
    public List<AgentModuleAliasDTO> list(@RequestParam String projectId) {
        return agentModuleAliasService.list(projectId);
    }

    @PostMapping("/module-alias")
    @Operation(summary = "新增模块别名")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_ADD)
    public AgentModuleAliasDTO add(@RequestBody @Valid AgentModuleAliasDTO request) {
        return agentModuleAliasService.add(request);
    }
}
