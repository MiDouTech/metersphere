package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentExecLogPageRequest;
import io.metersphere.agent.dto.AgentTokenListItemDTO;
import io.metersphere.agent.dto.AgentTokenPageRequest;
import io.metersphere.agent.service.AgentExecLogService;
import io.metersphere.agent.service.AgentTokenManagementService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.utils.Pager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Agent Token Governance")
@RestController
@RequestMapping({"/admin/agent-tokens", "/api/admin/agent-tokens"})
public class AdminAgentTokenController {
    @Resource
    private AgentTokenManagementService agentTokenManagementService;
    @Resource
    private AgentExecLogService agentExecLogService;

    @GetMapping
    @Operation(summary = "Admin list all Agent Tokens")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_READ)
    public Pager<List<AgentTokenListItemDTO>> page(AgentTokenPageRequest request) {
        return agentTokenManagementService.page(request);
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Admin revoke Agent Token")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_UPDATE)
    public void revoke(@PathVariable String id) {
        agentTokenManagementService.revoke(id);
    }

    @GetMapping("/audit/export")
    @Operation(summary = "Export Agent audit logs as CSV (max 5000). Partial governance M1.")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_READ)
    public ResponseEntity<byte[]> exportAudit(AgentExecLogPageRequest request) {
        byte[] body = agentExecLogService.exportCsv(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"agent-audit-export.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(body);
    }
}
