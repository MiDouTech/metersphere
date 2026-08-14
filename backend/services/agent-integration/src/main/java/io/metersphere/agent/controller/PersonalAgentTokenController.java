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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Personal Agent Token")
@RestController
@RequestMapping({"/personal/agent-tokens", "/api/personal/agent-tokens"})
public class PersonalAgentTokenController {
    @Resource
    private AgentTokenManagementService agentTokenManagementService;

    @PostMapping
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_CONNECT)
    @Operation(summary = "Create current user's Agent Token")
    public AgentTokenCreateResponse create(@RequestBody @Valid AgentTokenCreateRequest request) {
        return agentTokenManagementService.createPersonal(request);
    }

    @GetMapping
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_READ)
    @Operation(summary = "List current user's Agent Tokens")
    public Pager<List<AgentTokenListItemDTO>> page(AgentTokenPageRequest request) {
        return agentTokenManagementService.pagePersonal(request);
    }

    @PatchMapping("/{id}")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_CONNECT)
    @Operation(summary = "Update current user's Agent Token")
    public void update(@PathVariable String id, @RequestBody AgentTokenUpdateRequest request) {
        request.setId(id);
        agentTokenManagementService.updatePersonal(request);
    }

    @PostMapping("/{id}/disable")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_CONNECT)
    @Operation(summary = "Disable current user's Agent Token")
    public void disable(@PathVariable String id) {
        agentTokenManagementService.disablePersonal(id);
    }

    @PostMapping("/{id}/enable")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_CONNECT)
    @Operation(summary = "Enable current user's Agent Token")
    public void enable(@PathVariable String id) {
        agentTokenManagementService.enablePersonal(id);
    }

    @DeleteMapping("/{id}")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_REVOKE)
    @Operation(summary = "Delete current user's Agent Token")
    public void delete(@PathVariable String id) {
        agentTokenManagementService.deletePersonal(id);
    }

}
