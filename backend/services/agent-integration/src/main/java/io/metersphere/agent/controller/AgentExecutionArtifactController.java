package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.service.AgentExecutionArtifactService;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/ai/execution", "/api/ai/execution"})
public class AgentExecutionArtifactController {
    @Resource
    private AgentExecutionArtifactService artifactService;

    @GetMapping("/task/{taskId}/artifacts")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public List<AgentExecutionArtifactDTO> list(@PathVariable String taskId) {
        return artifactService.list(taskId);
    }

    @GetMapping("/task/{taskId}/artifact/{artifactId}")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public ResponseEntity<byte[]> download(@PathVariable String taskId, @PathVariable String artifactId) {
        return artifactService.download(taskId, artifactId);
    }
}
