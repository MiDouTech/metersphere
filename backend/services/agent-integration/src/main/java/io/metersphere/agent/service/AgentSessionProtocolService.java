package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.tool.AgentMcpToolRegistry;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read-only new-protocol discovery, separate from structure-only direct-execution contract. */
@Service
public class AgentSessionProtocolService {
    private static final Logger LOG = LoggerFactory.getLogger(AgentSessionProtocolService.class);
    public static final String TOOL_PREFIX = "metersphere.functional.execution.session.";
    public static final List<String> REQUIRED_OPERATIONS = AgentProtocolToolService.OPERATIONS;
    private final AgentBusinessAccess access;
    private final AgentSessionProtocolConfiguration configuration;
    private final ObjectProvider<AgentMcpToolRegistry> registry;

    public AgentSessionProtocolService(AgentBusinessAccess access, AgentSessionProtocolConfiguration configuration,
                                       ObjectProvider<AgentMcpToolRegistry> registry) {
        this.access = access;
        this.configuration = configuration;
        // Resolve only when called: registry construction itself collects the contract handler bean.
        this.registry = registry;
    }

    public Map<String, Object> contract(Map<String, Object> arguments) {
        if (arguments == null) throw new MSException("AGENT_VALIDATION_ERROR");
        String caseId = identifier(arguments.get("caseId"));
        String projectId = identifier(arguments.get("projectId"));
        access.caseProject(caseId, projectId, PermissionConstants.FUNCTIONAL_CASE_READ, false,
                AgentTokenScope.FUNCTIONAL_READ);
        if (!Set.of("caseId", "projectId").containsAll(arguments.keySet()))
            throw new MSException("AGENT_VALIDATION_ERROR");
        var readiness = configuration.executionReadiness();
        var handlers = registry.getObject();
        List<String> available = REQUIRED_OPERATIONS.stream()
                .filter(operation -> handlers.find(TOOL_PREFIX + operation).isPresent()).toList();
        List<String> missing = REQUIRED_OPERATIONS.stream().filter(operation -> !available.contains(operation)).toList();
        String traceId = UUID.randomUUID().toString();
        boolean availableForStart=readiness.configurationReady()&&missing.isEmpty();
        // Registration/configuration readiness is not authorization: start rechecks client, identity,
        // source policy, environment, governance and budgets in its committing transaction.
        if (readiness.issues().stream().anyMatch(issue -> issue.code().equals("CONFIGURATION_UNAVAILABLE"))) {
            LOG.warn("Session protocol discovery traceId={} stage=CONTRACT code=CONFIGURATION_UNAVAILABLE missingConfiguration={} missingOperations={}",
                    traceId, readiness.issues().size(), missing.size());
        } else {
            LOG.debug("Session protocol discovery traceId={} stage=CONTRACT code=PROTOCOL_UNAVAILABLE requested={} missingConfiguration={} missingOperations={}",
                    traceId, readiness.requested(), readiness.issues().size(), missing.size());
        }
        return Map.of(
                "protocolVersion", "1.0",
                "productVersion", "MSP-32/v1.7",
                "executionAvailable", availableForStart,
                "configurationReady", readiness.configurationReady(),
                "configurationIssues", readiness.issues(),
                "availableOperations", available,
                "missingOperations", missing,
                "activationBlockers", availableForStart?List.of():List.of(!readiness.configurationReady()?"PROTOCOL_CONFIGURATION_INCOMPLETE":"PROTOCOL_OPERATIONS_MISSING"),
                "cachePolicy", Map.of("knowledge", "MEMORY_ONLY", "observations", "MEMORY_ONLY"),
                "traceId", traceId);
    }

    private String identifier(Object value) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > 128)
            throw new MSException("AGENT_VALIDATION_ERROR");
        return text;
    }
}
