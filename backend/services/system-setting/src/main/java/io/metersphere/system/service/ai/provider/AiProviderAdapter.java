package io.metersphere.system.service.ai.provider;

import io.metersphere.system.dto.request.ai.AiProviderCapabilityDTO;
import io.metersphere.system.dto.request.ai.AiProviderTestRequest;
import io.metersphere.system.dto.request.ai.AiProviderTestResponse;
import io.metersphere.system.dto.request.ai.AiProviderChatRequest;
import io.metersphere.system.dto.request.ai.AiProviderInvocationResult;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.function.Consumer;

public interface AiProviderAdapter {
    AiProviderCapabilityDTO capability(String modelSourceId, String userId);

    AiProviderTestResponse testConnection(AiProviderTestRequest request, String userId);

    AiProviderInvocationResult invoke(AiProviderChatRequest request, String userId);

    AiProviderInvocationResult invokeAdmitted(AiProviderChatRequest request, String userId);

    Flux<String> stream(AiProviderChatRequest request, String userId);

    Flux<String> streamAdmitted(AiProviderChatRequest request, String userId);

    Flux<String> streamAdmittedWithTools(AiProviderChatRequest request, String userId, List<Object> tools);

    Flux<String> streamAdmittedWithTools(AiProviderChatRequest request, String userId, List<Object> tools,
                                         Consumer<String> selectedModelListener);
}
