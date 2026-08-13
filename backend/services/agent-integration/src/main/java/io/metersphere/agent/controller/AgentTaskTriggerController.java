package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentTaskTriggerDTO;
import io.metersphere.agent.dto.AgentTaskTriggerHistoryDTO;
import io.metersphere.agent.dto.AgentTaskTriggerRequest;
import io.metersphere.agent.service.AgentTaskTriggerService;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/ai/execution/triggers", "/api/ai/execution/triggers"})
public class AgentTaskTriggerController {
    @Resource
    private AgentTaskTriggerService service;

    @PostMapping
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public AgentTaskTriggerDTO create(@RequestBody @Valid AgentTaskTriggerRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public AgentTaskTriggerDTO update(@PathVariable String id,
                                      @RequestBody @Valid AgentTaskTriggerRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public AgentTaskTriggerDTO get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public List<AgentTaskTriggerDTO> list(@RequestParam String projectId) {
        return service.list(projectId);
    }

    @GetMapping("/{id}/history")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public List<AgentTaskTriggerHistoryDTO> history(@PathVariable String id,
                                                    @RequestParam(required = false) Integer limit) {
        return service.history(id, limit);
    }

    @PostMapping("/{id}/fire")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public AgentTaskTriggerHistoryDTO fire(@PathVariable String id) {
        return service.manualFire(id);
    }

    @PostMapping("/{id}/rotate-secret")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public AgentTaskTriggerDTO rotateSecret(@PathVariable String id) {
        return service.rotateSecret(id);
    }

    @PostMapping("/{id}/webhook")
    public AgentTaskTriggerHistoryDTO webhook(@PathVariable String id,
                                              @RequestHeader("X-MS-Event-Id") String eventId,
                                              @RequestHeader("X-MS-Timestamp") String timestamp,
                                              @RequestHeader("X-MS-Signature") String signature,
                                              @RequestBody String rawBody) {
        return service.webhook(id, eventId, timestamp, signature, rawBody);
    }
}
