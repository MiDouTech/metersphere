package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentMcpManifestDTO;
import io.metersphere.agent.service.AgentMcpBundleService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent MCP Bundle")
@RestController
@RequestMapping({"/agent/mcp", "/api/agent/mcp"})
public class AgentMcpController {
    @Resource
    private AgentMcpBundleService agentMcpBundleService;

    @GetMapping("/manifest")
    @Operation(summary = "MCP 包清单（版本/说明）")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_READ)
    public AgentMcpManifestDTO manifest() {
        return agentMcpBundleService.getManifest();
    }

    @GetMapping("/download")
    @Operation(summary = "下载 MCP 装配包 zip")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_READ)
    public ResponseEntity<byte[]> download() {
        return agentMcpBundleService.download();
    }
}
