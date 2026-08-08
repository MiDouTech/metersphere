package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.request.ai.AiOAuthAuthorizeRequest;
import io.metersphere.system.dto.request.ai.AiOAuthCallbackRequest;
import io.metersphere.system.dto.request.ai.AiOAuthConnectionRequest;
import io.metersphere.system.service.ai.provider.AiOAuthService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai/oauth")
public class AiOAuthController {
    @Resource private AiOAuthService service;

    @PostMapping("/connection")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG)
    public Map<String, Object> save(@Validated @RequestBody AiOAuthConnectionRequest request) {
        return service.save(request, SessionUtils.getUserId());
    }

    @PostMapping("/authorize")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG)
    public Map<String, String> authorize(@Validated @RequestBody AiOAuthAuthorizeRequest request) {
        return service.authorize(request, SessionUtils.getUserId());
    }

    @PostMapping("/callback")
    public Map<String, Object> callback(@Validated @RequestBody AiOAuthCallbackRequest request) {
        return service.callback(request);
    }

    @PostMapping("/{id}/refresh")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG)
    public Map<String, Object> refresh(@PathVariable String id) {
        return service.forceRefresh(id, SessionUtils.getUserId());
    }

    @PostMapping("/{id}/revoke")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG)
    public void revoke(@PathVariable String id) {
        service.revoke(id, SessionUtils.getUserId());
    }

    @GetMapping("/{id}")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG)
    public Map<String, Object> status(@PathVariable String id) {
        return service.status(id, SessionUtils.getUserId());
    }
}
