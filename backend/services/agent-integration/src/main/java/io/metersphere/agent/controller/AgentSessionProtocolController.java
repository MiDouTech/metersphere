package io.metersphere.agent.controller;

import io.metersphere.agent.service.AgentSessionProtocolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.util.MultiValueMap;
import io.metersphere.sdk.exception.MSException;

@RestController
@RequestMapping({"/agent/v1/execution-sessions", "/api/agent/v1/execution-sessions"})
public class AgentSessionProtocolController {
    private final AgentSessionProtocolService service;

    public AgentSessionProtocolController(AgentSessionProtocolService service) { this.service = service; }

    @GetMapping("/contract")
    public Map<String, Object> contract(@RequestParam MultiValueMap<String, String> parameters) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        parameters.forEach((key, values) -> {
            if (values.size() != 1) throw new MSException("AGENT_VALIDATION_ERROR");
            arguments.put(key, values.getFirst());
        });
        return service.contract(arguments);
    }
}
