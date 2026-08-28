package io.metersphere.agent.controller;
import io.metersphere.agent.dto.*;
import io.metersphere.agent.service.AgentExecutionCheckpointService;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping({"/ai/execution/tasks/{taskId}/checkpoints","/api/ai/execution/tasks/{taskId}/checkpoints"})
public class AgentExecutionCheckpointController {
 @Resource private AgentExecutionCheckpointService service;
 @PostMapping @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN) public AgentExecutionCheckpointDTO create(@PathVariable String taskId,@RequestBody @Valid AgentCheckpointCreateRequest r){return service.create(taskId,r);}
 @PostMapping("/{id}/resume") @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN) public AgentExecutionCheckpointDTO resume(@PathVariable String taskId,@PathVariable String id,@RequestBody @Valid AgentCheckpointResumeRequest r){return service.resume(taskId,id,r);}
}
