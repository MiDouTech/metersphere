package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.request.ai.AiAgentGatewayCapabilityDTO;
import io.metersphere.system.dto.request.ai.AiAgentGatewayRequest;
import io.metersphere.system.dto.request.ai.AiAgentGatewayInvokeRequest;
import io.metersphere.system.service.ai.provider.AiAgentGatewayService;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.system.security.CheckOwner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.validation.annotation.Validated;
import java.util.Map;

@RestController
@RequestMapping("/ai/agent-gateway")
@Tag(name = "AI Agent Gateway")
public class AiAgentGatewayController {
    @Resource
    private AiAgentGatewayService aiAgentGatewayService;

    @GetMapping("/capability/{gatewayId}")
    @Operation(summary = "企业 Agent 网关能力声明")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG)
    public AiAgentGatewayCapabilityDTO capability(@PathVariable String gatewayId) {
        return aiAgentGatewayService.capability(gatewayId, SessionUtils.getUserId());
    }

    @PostMapping
    @Operation(summary = "配置企业 Agent 网关")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG)
    public AiAgentGatewayCapabilityDTO save(@Validated @RequestBody AiAgentGatewayRequest request) {
        return aiAgentGatewayService.save(request, SessionUtils.getUserId());
    }

    @PostMapping("/invoke")
    @Operation(summary = "调用企业 Agent 网关")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_GENERATE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public Map<?, ?> invoke(@Validated @RequestBody AiAgentGatewayInvokeRequest request) {
        return aiAgentGatewayService.invoke(request, SessionUtils.getUserId());
    }

    @PostMapping("/{gatewayId}/disable")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG)
    public void disable(@PathVariable String gatewayId) {
        aiAgentGatewayService.disable(gatewayId, SessionUtils.getUserId());
    }
}
