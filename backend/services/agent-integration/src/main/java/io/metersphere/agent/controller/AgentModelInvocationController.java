package io.metersphere.agent.controller;

import io.metersphere.agent.service.AgentModelInvocationService;
import io.metersphere.agent.service.AgentProjectService;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping({"/ai/model-invocations","/api/ai/model-invocations"})
public class AgentModelInvocationController {
    @Resource private AgentModelInvocationService service;
    @Resource private AgentProjectService projects;
    @GetMapping("/{id}") @RequiresPermissions(PermissionConstants.AI_MODEL_READ)
    public Map<String,Object> get(@PathVariable String id,@RequestParam String projectId){return service.get(id,projects.resolveProjectId(projectId));}
    @GetMapping("/usage") @RequiresPermissions(PermissionConstants.AI_MODEL_READ)
    public Map<String,Object> usage(@RequestParam String projectId,@RequestParam(required=false)Long from,@RequestParam(required=false)Long to){return service.usage(projects.resolveProjectId(projectId),from,to);}
}
