package io.metersphere.agent.controller;

import io.metersphere.agent.dto.*;
import io.metersphere.agent.service.AgentCredentialReferenceService;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/ai/credential-references", "/api/ai/credential-references"})
public class AgentCredentialReferenceController {
    @Resource private AgentCredentialReferenceService service;
    @GetMapping @RequiresPermissions(PermissionConstants.AI_CREDENTIAL_READ_METADATA)
    public List<AgentCredentialReferenceDTO> list(@RequestParam String projectId, @RequestParam(required=false) String environmentId) { return service.list(projectId, environmentId); }
    @GetMapping("/{id}") @RequiresPermissions(PermissionConstants.AI_CREDENTIAL_READ_METADATA)
    public AgentCredentialReferenceDTO get(@PathVariable String id) { return service.getMetadata(id); }
    @PostMapping @RequiresPermissions(PermissionConstants.AI_CREDENTIAL_MANAGE)
    public AgentCredentialReferenceDTO create(@RequestBody @Valid AgentCredentialReferenceRequest request) { return service.create(request); }
    @PutMapping("/{id}") @RequiresPermissions(PermissionConstants.AI_CREDENTIAL_MANAGE)
    public AgentCredentialReferenceDTO update(@PathVariable String id,@RequestBody @Valid AgentCredentialReferenceRequest request) { return service.update(id,request); }
    @PostMapping("/{id}/verify") @RequiresPermissions(PermissionConstants.AI_CREDENTIAL_VERIFY)
    public AgentCredentialVerifyResult verify(@PathVariable String id) { return service.verify(id); }
    @PostMapping("/{id}/enable") @RequiresPermissions(PermissionConstants.AI_CREDENTIAL_MANAGE)
    public AgentCredentialReferenceDTO enable(@PathVariable String id) { return service.setEnabled(id, true); }
    @PostMapping("/{id}/disable") @RequiresPermissions(PermissionConstants.AI_CREDENTIAL_MANAGE)
    public AgentCredentialReferenceDTO disable(@PathVariable String id) { return service.setEnabled(id, false); }
}
