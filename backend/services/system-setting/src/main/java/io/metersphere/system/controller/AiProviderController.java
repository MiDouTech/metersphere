package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.request.ai.AiProviderCapabilityDTO;
import io.metersphere.system.dto.request.ai.AiProviderTestRequest;
import io.metersphere.system.dto.request.ai.AiProviderTestResponse;
import io.metersphere.system.dto.request.ai.AiProviderChatRequest;
import io.metersphere.system.dto.request.ai.AiProviderInvocationResult;
import io.metersphere.system.service.ai.provider.AiProviderAdapter;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.system.security.CheckOwner;
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
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

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

    @PostMapping("/invoke")
    @Operation(summary = "AI Provider 统一调用")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public AiProviderInvocationResult invoke(@Validated @RequestBody AiProviderChatRequest request) {
        return aiProviderAdapter.invoke(request, SessionUtils.getUserId());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "AI Provider 统一流式调用")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public Flux<String> stream(@Validated @RequestBody AiProviderChatRequest request) {
        return aiProviderAdapter.stream(request, SessionUtils.getUserId());
    }
}
