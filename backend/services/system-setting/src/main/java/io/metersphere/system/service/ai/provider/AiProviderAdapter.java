package io.metersphere.system.service.ai.provider;

import io.metersphere.system.dto.request.ai.AiProviderCapabilityDTO;
import io.metersphere.system.dto.request.ai.AiProviderTestRequest;
import io.metersphere.system.dto.request.ai.AiProviderTestResponse;

public interface AiProviderAdapter {
    AiProviderCapabilityDTO capability(String modelSourceId, String userId);

    AiProviderTestResponse testConnection(AiProviderTestRequest request, String userId);
}
