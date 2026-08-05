package io.metersphere.system.service.ai.provider;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.request.ai.AIChatOption;
import io.metersphere.system.dto.request.ai.AIChatRequest;
import io.metersphere.system.dto.request.ai.AiModelSourceDTO;
import io.metersphere.system.dto.request.ai.AiProviderCapabilityDTO;
import io.metersphere.system.dto.request.ai.AiProviderTestRequest;
import io.metersphere.system.dto.request.ai.AiProviderTestResponse;
import io.metersphere.system.service.AiChatBaseService;
import io.metersphere.system.service.SystemAIConfigService;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DefaultAiProviderAdapter implements AiProviderAdapter {
    @Resource
    private SystemAIConfigService systemAIConfigService;
    @Resource
    private AiChatBaseService aiChatBaseService;

    @Override
    public AiProviderCapabilityDTO capability(String modelSourceId, String userId) {
        AiModelSourceDTO model = systemAIConfigService.getModelSourceDTO(modelSourceId, userId);
        AiProviderCapabilityDTO capability = new AiProviderCapabilityDTO();
        capability.setModelSourceId(model.getId());
        capability.setProviderName(model.getProviderName());
        capability.setBaseName(model.getBaseName());
        capability.setStreamSupported(false);
        capability.setOauthSupported(false);
        capability.setAgentGatewaySupported(false);
        capability.setFeatures(List.of("CHAT_COMPLETION", "CASE_GENERATION"));
        return capability;
    }

    @Override
    public AiProviderTestResponse testConnection(AiProviderTestRequest request, String userId) {
        long start = System.currentTimeMillis();
        AiProviderTestResponse response = new AiProviderTestResponse();
        try {
            AIChatRequest aiChatRequest = new AIChatRequest();
            aiChatRequest.setChatModelId(request.getChatModelId());
            aiChatRequest.setPrompt(StringUtils.defaultIfBlank(request.getPrompt(), "请回复 OK"));
            aiChatRequest.setConversationId(StringUtils.defaultIfBlank(request.getConversationId(), IDGenerator.nextStr()));
            aiChatRequest.setOrganizationId(request.getOrganizationId());
            AiModelSourceDTO module = aiChatBaseService.getModule(aiChatRequest, userId);
            String content = aiChatBaseService.chat(AIChatOption.builder()
                    .conversationId(aiChatRequest.getConversationId())
                    .module(module)
                    .prompt(aiChatRequest.getPrompt())
                    .build()).content();
            response.setSuccess(true);
            response.setContent(StringUtils.left(content, 1000));
        } catch (Exception ex) {
            response.setSuccess(false);
            response.setMessage(sanitize(ex.getMessage()));
        } finally {
            response.setDurationMs(System.currentTimeMillis() - start);
            log.info("ai_provider_test result={}", JSON.toJSONString(response));
        }
        return response;
    }

    private String sanitize(String message) {
        if (StringUtils.isBlank(message)) {
            return "连接测试失败";
        }
        return message.replaceAll("(?i)(api[-_ ]?key|token|secret|authorization)\\s*[:=]\\s*[^\\s,;]+", "$1=******");
    }
}
