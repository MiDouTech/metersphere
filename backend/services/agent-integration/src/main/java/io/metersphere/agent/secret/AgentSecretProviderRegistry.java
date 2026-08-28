package io.metersphere.agent.secret;

import io.metersphere.sdk.exception.MSException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AgentSecretProviderRegistry {
    private final Map<String, AgentSecretProvider> providers;

    public AgentSecretProviderRegistry(List<AgentSecretProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                item -> item.type().toUpperCase(Locale.ROOT), Function.identity()));
    }

    public AgentSecretProvider require(String type) {
        AgentSecretProvider provider = providers.get(type == null ? "" : type.toUpperCase(Locale.ROOT));
        if (provider == null) throw new MSException("不支持的 Secret Provider");
        return provider;
    }
}
