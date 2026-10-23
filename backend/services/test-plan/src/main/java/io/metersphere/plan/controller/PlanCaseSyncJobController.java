package io.metersphere.plan.controller;

import io.metersphere.plan.service.PlanCaseSyncJobService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.security.CheckOwner;
import io.metersphere.system.utils.SessionUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/test-plan/functional/case/sync-jobs")
@RequiresPermissions({PermissionConstants.FUNCTIONAL_CASE_READ, PermissionConstants.TEST_PLAN_READ_ASSOCIATION})
public class PlanCaseSyncJobController {
    private final PlanCaseSyncJobService service;
    public PlanCaseSyncJobController(PlanCaseSyncJobService service) { this.service = service; }

    public record Create(@NotBlank String projectId, @NotBlank @Size(max=128) String requestId) { }

    @PostMapping
    @CheckOwner(resourceId="#request.projectId()", resourceType="project")
    public Map<String, Object> create(@RequestBody @Valid Create request) {
        return service.create(request.projectId(), SessionUtils.getUserId(), request.requestId());
    }
    @GetMapping("/{id}") public Map<String, Object> get(@PathVariable String id) { return service.get(id, SessionUtils.getUserId()); }
    @PostMapping("/{id}/cancel") public Map<String, Object> cancel(@PathVariable String id) { return service.cancel(id, SessionUtils.getUserId()); }
    @PostMapping("/{id}/retry") public Map<String, Object> retry(@PathVariable String id) { return service.retry(id, SessionUtils.getUserId()); }
}
