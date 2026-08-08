package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.request.ai.AiProjectGovernanceDTO;
import io.metersphere.system.security.CheckOwner;
import io.metersphere.system.service.ai.AiGovernanceService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/governance")
public class AiGovernanceController {
    @Resource
    private AiGovernanceService service;

    @GetMapping
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG)
    @CheckOwner(resourceId = "#projectId", resourceType = "project")
    public AiProjectGovernanceDTO get(@RequestParam String projectId) {
        return service.get(projectId);
    }

    @PostMapping
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public AiProjectGovernanceDTO save(@Validated @RequestBody AiProjectGovernanceDTO request) {
        return service.save(request, SessionUtils.getUserId());
    }
}
