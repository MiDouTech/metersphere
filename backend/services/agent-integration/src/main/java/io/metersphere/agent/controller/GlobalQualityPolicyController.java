package io.metersphere.agent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.metersphere.agent.quality.GlobalQualityPolicyService;
import io.metersphere.agent.quality.QualityPolicyValidator;
import io.metersphere.sdk.constants.PermissionConstants;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/system/quality", "/api/system/quality"})
public class GlobalQualityPolicyController {
    private final GlobalQualityPolicyService service;
    public GlobalQualityPolicyController(GlobalQualityPolicyService service) { this.service = service; }

    @ModelAttribute
    public void rejectProjectSelection(jakarta.servlet.http.HttpServletRequest request) {
        if (request.getParameterMap().containsKey("projectId") || request.getParameterMap().containsKey("pId")) {
            throw new IllegalArgumentException("Project selection is not supported");
        }
    }

    @GetMapping("/legacy-policies")
    @RequiresPermissions(PermissionConstants.SYSTEM_QUALITY_READ)
    public GlobalQualityPolicyService.Archive archive(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize) { return service.archive(page,pageSize); }

    @PostMapping("/legacy-policies/{id}/import")
    @RequiresPermissions(PermissionConstants.SYSTEM_QUALITY_MANAGE)
    public GlobalQualityPolicyService.Policy importLegacy(@PathVariable String id) { return service.importLegacy(id); }

    @GetMapping("/policy-schema")
    @RequiresPermissions(PermissionConstants.SYSTEM_QUALITY_READ)
    public JsonNode schema() { return service.schema(); }

    @GetMapping("/policies")
    @RequiresPermissions(PermissionConstants.SYSTEM_QUALITY_READ)
    public GlobalQualityPolicyService.Listing list(
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return service.list(page, pageSize);
    }

    @GetMapping("/policies/{id}")
    @RequiresPermissions(PermissionConstants.SYSTEM_QUALITY_READ)
    public GlobalQualityPolicyService.Detail detail(@PathVariable String id) {
        return service.detail(id);
    }

    @PostMapping("/policies/validate")
    @RequiresPermissions(PermissionConstants.SYSTEM_QUALITY_MANAGE)
    public QualityPolicyValidator.Validation validate(@RequestBody @Valid GlobalQualityPolicyService.DraftRequest request) {
        return service.validate(request);
    }

    @PostMapping("/policies")
    @RequiresPermissions(PermissionConstants.SYSTEM_QUALITY_MANAGE)
    public GlobalQualityPolicyService.Policy create(@RequestBody @Valid GlobalQualityPolicyService.DraftRequest request) {
        return service.create(request);
    }

    @PutMapping("/policies/{id}/draft")
    @RequiresPermissions(PermissionConstants.SYSTEM_QUALITY_MANAGE)
    public GlobalQualityPolicyService.Policy update(@PathVariable String id, @RequestBody @Valid GlobalQualityPolicyService.DraftRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/policies/{id}/publish")
    @RequiresPermissions(PermissionConstants.SYSTEM_QUALITY_PUBLISH)
    public GlobalQualityPolicyService.Policy publish(@PathVariable String id, @RequestBody @Valid GlobalQualityPolicyService.PublishRequest request) {
        return service.publish(id, request);
    }
}
