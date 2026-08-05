package io.metersphere.system.service.ai.provider;

import io.metersphere.system.dto.request.ai.AiAgentGatewayCapabilityDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class AiAgentGatewayService {
    private static final Set<String> KNOWN_PRODUCT_NAMES = Set.of("cursor", "codex", "workbuddy");

    public AiAgentGatewayCapabilityDTO capability(String gatewayId) {
        AiAgentGatewayCapabilityDTO dto = new AiAgentGatewayCapabilityDTO();
        dto.setGatewayId(gatewayId);
        dto.setName(StringUtils.defaultIfBlank(gatewayId, "default"));
        dto.setProtocol("MCP_OR_CUSTOM_GATEWAY");
        dto.setConfigured(false);
        dto.setOauthSupported(false);
        dto.setQuotaSupported(false);
        dto.setFeatures(List.of());
        if (KNOWN_PRODUCT_NAMES.contains(StringUtils.lowerCase(gatewayId))) {
            dto.setMessage("该产品名称不能直接视为开放模型 API；需接入官方开放能力或企业 Agent 网关后才可启用。");
        } else {
            dto.setMessage("企业 Agent 网关尚未配置，当前仅返回能力声明占位。");
        }
        log.info("ai_agent_gateway_capability gatewayId={}, configured=false", gatewayId);
        return dto;
    }
}
