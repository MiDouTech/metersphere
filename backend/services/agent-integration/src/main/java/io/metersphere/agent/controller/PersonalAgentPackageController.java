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

@Tag(name = "Personal Agent Package")
@RestController
@RequestMapping({"/personal/agent-package", "/api/personal/agent-package"})
public class PersonalAgentPackageController {
    @Resource
    private AgentMcpBundleService agentMcpBundleService;

    @GetMapping("/manifest")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_READ)
    @Operation(summary = "AI skill package manifest")
    public AgentMcpManifestDTO manifest() {
        return agentMcpBundleService.getManifest();
    }

    @GetMapping("/skill/download")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERSONAL_AI_AGENT_READ)
    @Operation(summary = "Download AI skill package without token")
    public ResponseEntity<byte[]> download() {
        return agentMcpBundleService.download();
    }
}
