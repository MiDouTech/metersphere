package io.metersphere.agent.controller;

import io.metersphere.agent.dto.*;import io.metersphere.agent.service.AgentLoginProfileService;import io.metersphere.sdk.constants.PermissionConstants;import jakarta.annotation.Resource;import jakarta.validation.Valid;import org.apache.shiro.authz.annotation.RequiresPermissions;import org.springframework.web.bind.annotation.*;import java.util.List;

@RestController @RequestMapping({"/ai/login-profiles","/api/ai/login-profiles"})
public class AgentLoginProfileController {
 @Resource private AgentLoginProfileService service;
 @GetMapping @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ) public List<AgentLoginProfileDTO> list(@RequestParam String projectId){return service.list(projectId);}
 @PostMapping @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN) public AgentLoginProfileDTO create(@RequestBody @Valid AgentLoginProfileRequest r){return service.create(r);}
 @PutMapping("/{id}") @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN) public AgentLoginProfileDTO update(@PathVariable String id,@RequestBody @Valid AgentLoginProfileRequest r){return service.update(id,r);}
 @PostMapping("/{id}/enable") @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN) public AgentLoginProfileDTO enable(@PathVariable String id){return service.setEnabled(id,true);}
 @PostMapping("/{id}/disable") @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN) public AgentLoginProfileDTO disable(@PathVariable String id){return service.setEnabled(id,false);}
}
