package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.request.ai.AiProviderCapabilityDTO;
import io.metersphere.system.dto.request.ai.AiProviderTestRequest;
import io.metersphere.system.dto.request.ai.AiProviderTestResponse;
import io.metersphere.system.service.ai.provider.AiProviderAdapter;
import io.metersphere.system.utils.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/provider")
@Tag(name = "AI Provider Adapter")
public class AiProviderController {
    @Resource
    private AiProviderAdapter aiProviderAdapter;

    @GetMapping("/capability/{modelSourceId}")
    @Operation(summary = "AI Provider 能力声明")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_AI_MODEL_READ)
    public AiProviderCapabilityDTO capability(@PathVariable String modelSourceId) {
        return aiProviderAdapter.capability(modelSourceId, SessionUtils.getUserId());
    }

    @PostMapping("/test-connect")
    @Operation(summary = "AI Provider 连接测试")
    @RequiresPermissions(PermissionConstants.SYSTEM_PARAMETER_SETTING_AI_MODEL_READ)
    public AiProviderTestResponse testConnection(@Validated @RequestBody AiProviderTestRequest request) {
        return aiProviderAdapter.testConnection(request, SessionUtils.getUserId());
    }
}
