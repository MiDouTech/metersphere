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

/**
 * @deprecated 历史命名入口。新用户请使用 {@code /api/personal/agent-package/**} 下载 AI 技能包。
 */
@Deprecated
@Tag(name = "Agent MCP Bundle (Deprecated)")
@RestController
@RequestMapping({"/agent/mcp", "/api/agent/mcp"})
public class AgentMcpController {
    @Resource
    private AgentMcpBundleService agentMcpBundleService;

    @GetMapping("/manifest")
    @Operation(summary = "[Deprecated] 转发至个人 AI 技能包清单；请改用 /personal/agent-package/manifest")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_READ)
    public AgentMcpManifestDTO manifest() {
        return agentMcpBundleService.getManifest();
    }

    @GetMapping("/download")
    @Operation(summary = "[Deprecated] 转发至个人 AI 技能包下载；请改用 /personal/agent-package/skill/download")
    @RequiresPermissions(PermissionConstants.SYSTEM_USER_READ)
    public ResponseEntity<byte[]> download() {
        return agentMcpBundleService.download();
    }
}
