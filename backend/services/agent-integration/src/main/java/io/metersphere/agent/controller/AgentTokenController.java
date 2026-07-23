package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentTokenCreateRequest;
import io.metersphere.agent.dto.AgentTokenCreateResponse;
import io.metersphere.agent.dto.AgentTokenListItemDTO;
import io.metersphere.agent.dto.AgentTokenPageRequest;
import io.metersphere.agent.dto.AgentTokenUpdateRequest;
import io.metersphere.agent.service.AgentTokenManagementService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.utils.Pager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Agent Token Management")
@RestController
@RequestMapping({"/agent/token", "/api/agent/token"})
public class AgentTokenController {
    @Resource
    private AgentTokenManagementService agentTokenManagementService;

    @PostMapping("/add")
    @Operation(summary = "创建 Agent Token")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_ADD)
    public AgentTokenCreateResponse add(@RequestBody @Valid AgentTokenCreateRequest request) {
        return agentTokenManagementService.create(request);
    }

    @PostMapping("/page")
    @Operation(summary = "Token 分页列表")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_READ)
    public Pager<List<AgentTokenListItemDTO>> page(@RequestBody AgentTokenPageRequest request) {
        return agentTokenManagementService.page(request);
    }

    @GetMapping("/page")
    @Operation(summary = "Token 分页列表（GET）")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_READ)
    public Pager<List<AgentTokenListItemDTO>> pageGet(AgentTokenPageRequest request) {
        return agentTokenManagementService.page(request);
    }

    @PostMapping("/update")
    @Operation(summary = "更新 Token")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_UPDATE)
    public void update(@RequestBody @Valid AgentTokenUpdateRequest request) {
        agentTokenManagementService.update(request);
    }

    @GetMapping("/delete/{id}")
    @Operation(summary = "删除 Token")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_DELETE)
    public void delete(@PathVariable String id) {
        agentTokenManagementService.delete(id);
    }
}
