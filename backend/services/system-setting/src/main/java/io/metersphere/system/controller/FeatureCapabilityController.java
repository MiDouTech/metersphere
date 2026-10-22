package io.metersphere.system.controller;

import io.metersphere.system.security.LlmFeature;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class FeatureCapabilityController {
    @Resource private LlmFeature llmFeature;

    @GetMapping("/feature/capabilities")
    public Map<String, Boolean> capabilities() {
        return Map.of("llmUiEnabled", llmFeature.isEnabled());
    }
}
