package io.metersphere.agent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.metersphere.agent.quality.QualityPolicyService;
import io.metersphere.agent.quality.QualityPolicyValidator;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/quality", "/api/quality"})
public class QualityPolicyController {
    private final QualityPolicyService service;
    public QualityPolicyController(QualityPolicyService service) { this.service = service; }

    @GetMapping("/policy-schema")
    @RequiresPermissions(PermissionConstants.QUALITY_READ)
    public JsonNode schema(@RequestParam String projectId) { return service.schema(projectId); }

    @GetMapping("/policies")
    @RequiresPermissions(PermissionConstants.QUALITY_READ)
    public QualityPolicyService.Listing list(@RequestParam String projectId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return service.list(projectId, page, pageSize);
    }

    @GetMapping("/policies/{id}")
    @RequiresPermissions(PermissionConstants.QUALITY_READ)
    public QualityPolicyService.Detail detail(@PathVariable String id, @RequestParam String projectId) {
        return service.detail(id, projectId);
    }

    @PostMapping("/policies/validate")
    @RequiresPermissions(PermissionConstants.QUALITY_POLICY_MANAGE)
    public QualityPolicyValidator.Validation validate(@RequestBody @Valid QualityPolicyService.DraftRequest request) {
        throw new io.metersphere.sdk.exception.MSException("QUALITY_POLICY_LEGACY_WRITE_FORBIDDEN");
    }

    @PostMapping("/policies")
    @RequiresPermissions(PermissionConstants.QUALITY_POLICY_MANAGE)
    public QualityPolicyService.Policy create(@RequestBody @Valid QualityPolicyService.DraftRequest request) {
        throw new io.metersphere.sdk.exception.MSException("QUALITY_POLICY_LEGACY_WRITE_FORBIDDEN");
    }

    @PutMapping("/policies/{id}/draft")
    @RequiresPermissions(PermissionConstants.QUALITY_POLICY_MANAGE)
    public QualityPolicyService.Policy update(@PathVariable String id, @RequestBody @Valid QualityPolicyService.DraftRequest request) {
        throw new io.metersphere.sdk.exception.MSException("QUALITY_POLICY_LEGACY_WRITE_FORBIDDEN");
    }

    @PostMapping("/policies/{id}/publish")
    @RequiresPermissions(PermissionConstants.QUALITY_POLICY_PUBLISH)
    public QualityPolicyService.Policy publish(@PathVariable String id, @RequestBody @Valid QualityPolicyService.PublishRequest request) {
        throw new io.metersphere.sdk.exception.MSException("QUALITY_POLICY_LEGACY_WRITE_FORBIDDEN");
    }
}
