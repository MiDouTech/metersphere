package io.metersphere.system.config;

import io.metersphere.sdk.util.ShiroFilterChainExtender;
import jakarta.servlet.Filter;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WecomBotShiroFilterChainExtender implements ShiroFilterChainExtender {
    @Override
    public void extend(Map<String, Filter> filters, Map<String, String> chain) {
        // The controller performs timestamped HMAC machine authentication before parsing the callback body.
        chain.put("/internal/wecom-bot/events/**", "anon");
    }
}
