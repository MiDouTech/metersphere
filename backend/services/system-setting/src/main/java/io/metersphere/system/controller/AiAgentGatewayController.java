package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.request.ai.AiAgentGatewayCapabilityDTO;
import io.metersphere.system.service.ai.provider.AiAgentGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return aiAgentGatewayService.capability(gatewayId);
    }
}
