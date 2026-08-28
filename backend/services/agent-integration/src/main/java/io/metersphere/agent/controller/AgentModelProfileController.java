package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentModelProfileDTO;
import io.metersphere.agent.dto.AgentModelProfileRequest;
import io.metersphere.agent.service.AgentModelProfileService;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/ai/model-profiles","/api/ai/model-profiles"})
public class AgentModelProfileController {
    @Resource private AgentModelProfileService service;
    @GetMapping @RequiresPermissions(PermissionConstants.AI_MODEL_READ) public List<AgentModelProfileDTO> list(@RequestParam String projectId){return service.list(projectId);}
    @GetMapping("/{id}") @RequiresPermissions(PermissionConstants.AI_MODEL_READ) public AgentModelProfileDTO get(@PathVariable String id){return service.get(id);}
    @PostMapping @RequiresPermissions(PermissionConstants.AI_MODEL_MANAGE) public AgentModelProfileDTO create(@RequestBody @Valid AgentModelProfileRequest r){return service.create(r);}
    @PutMapping("/{id}") @RequiresPermissions(PermissionConstants.AI_MODEL_MANAGE) public AgentModelProfileDTO update(@PathVariable String id,@RequestBody @Valid AgentModelProfileRequest r){return service.update(id,r);}
    @PostMapping("/{id}/verify") @RequiresPermissions(PermissionConstants.AI_MODEL_VERIFY) public Map<String,Object> verify(@PathVariable String id){return service.verify(id);}
    @PostMapping("/{id}/enable") @RequiresPermissions(PermissionConstants.AI_MODEL_MANAGE) public AgentModelProfileDTO enable(@PathVariable String id){return service.setEnabled(id,true);}
    @PostMapping("/{id}/disable") @RequiresPermissions(PermissionConstants.AI_MODEL_MANAGE) public AgentModelProfileDTO disable(@PathVariable String id){return service.setEnabled(id,false);}
    @GetMapping("/{id}/health") @RequiresPermissions(PermissionConstants.AI_MODEL_READ) public Map<String,Object> health(@PathVariable String id){return service.health(id);}
    @GetMapping("/{id}/capabilities") @RequiresPermissions(PermissionConstants.AI_MODEL_READ) public Map<String,Object> capabilities(@PathVariable String id){return service.capabilities(id);}
}
