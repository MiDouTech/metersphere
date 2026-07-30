package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentMcpManifestDTO;
import io.metersphere.agent.service.AgentMcpBundleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
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
    @Operation(summary = "AI skill package manifest")
    public AgentMcpManifestDTO manifest() {
        return agentMcpBundleService.getManifest();
    }

    @GetMapping("/skill/download")
    @Operation(summary = "Download AI skill package without token")
    public ResponseEntity<byte[]> download() {
        return agentMcpBundleService.download();
    }
}
