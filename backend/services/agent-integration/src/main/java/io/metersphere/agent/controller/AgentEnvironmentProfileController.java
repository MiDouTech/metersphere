package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentEnvironmentProfileDTO;
import io.metersphere.agent.dto.AgentEnvironmentProfileRequest;
import io.metersphere.agent.dto.AgentEnvironmentVerifyResult;
import io.metersphere.agent.dto.AgentEnvironmentVerifyRequest;
import io.metersphere.agent.service.AgentEnvironmentProfileService;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/ai/environment-profiles", "/api/ai/environment-profiles"})
public class AgentEnvironmentProfileController {
    @Resource private AgentEnvironmentProfileService service;

    @GetMapping
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public List<AgentEnvironmentProfileDTO> list(@RequestParam String projectId) { return service.list(projectId); }

    @GetMapping("/{id}")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public AgentEnvironmentProfileDTO get(@PathVariable String id) { return service.get(id); }

    @PostMapping
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public AgentEnvironmentProfileDTO create(@RequestBody @Valid AgentEnvironmentProfileRequest request) { return service.create(request); }

    @PutMapping("/{id}")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public AgentEnvironmentProfileDTO update(@PathVariable String id, @RequestBody @Valid AgentEnvironmentProfileRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/verify")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public AgentEnvironmentVerifyResult verify(@PathVariable String id,
                                                @RequestBody(required = false) @Valid AgentEnvironmentVerifyRequest request) {
        return service.verify(id, request == null ? new AgentEnvironmentVerifyRequest() : request);
    }

    @PostMapping("/{id}/enable")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public AgentEnvironmentProfileDTO enable(@PathVariable String id) { return service.setEnabled(id, true); }

    @PostMapping("/{id}/disable")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public AgentEnvironmentProfileDTO disable(@PathVariable String id) { return service.setEnabled(id, false); }
}
