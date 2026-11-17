package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentSessionProtocolService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class AgentSessionProtocolTools {
    @Bean
    public AgentMcpToolHandler sessionProtocolContract(AgentSessionProtocolService service) {
        return new AgentMcpToolHandler() {
            public String name() { return AgentSessionProtocolService.TOOL_PREFIX + "contract"; }
            public String description() {
                return "Read new session-protocol readiness for an authorized case. Does not start execution, "
                        + "grant permissions, verify results or alter the legacy direct-execution contract. "
                        + "executionAvailable=false means callers must not enter the new protocol.";
            }
            public String requiredScope() { return AgentTokenScope.FUNCTIONAL_READ; }
            public Map<String, Object> inputSchema() {
                return AgentMcpToolSchemas.object(Map.of("projectId", AgentMcpToolSchemas.string(1, 128),
                        "caseId", AgentMcpToolSchemas.string(1, 128)), List.of("projectId", "caseId"));
            }
            public Map<String, Object> annotations() {
                return AgentMcpToolSchemas.annotations(requiredScope(), true, false, false);
            }
            public Object execute(Map<String, Object> arguments) { return service.contract(arguments); }
        };
    }
}
