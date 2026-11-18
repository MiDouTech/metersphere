package io.metersphere.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.controller.AgentSessionProtocolController;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolRegistry;
import io.metersphere.agent.tool.functional.AgentSessionProtocolTools;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real shared authorization queries and REST/MCP handler calls; not deployment or full-protocol acceptance. */
class AgentSessionProtocolTests {
    private JdbcTemplate jdbc;
    private MockEnvironment environment;
    private AgentSessionProtocolConfiguration configuration;
    private AgentSessionProtocolService service;
    private AgentMcpToolHandler tool;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:session_" + UUID.randomUUID()
                + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE agent_token(id VARCHAR(64),user_id VARCHAR(64),enable BOOLEAN,status VARCHAR(32),revoked_at BIGINT,expire_time BIGINT,scopes VARCHAR(255),project_ids VARCHAR(255))");
        jdbc.execute("CREATE TABLE project(id VARCHAR(64),deleted INT,enable INT)");
        jdbc.execute("CREATE TABLE functional_case(id VARCHAR(64),project_id VARCHAR(64),deleted INT)");
        jdbc.execute("CREATE TABLE user(id VARCHAR(64),enable INT,deleted INT)");
        jdbc.execute("CREATE TABLE user_role_relation(user_id VARCHAR(64),role_id VARCHAR(64),source_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE user_role(id VARCHAR(64),enabled INT,type VARCHAR(32))");
        jdbc.execute("CREATE TABLE user_role_permission(role_id VARCHAR(64),permission_id VARCHAR(128))");
        jdbc.update("INSERT INTO agent_token VALUES ('t','u',true,'ACTIVE',null,null,?,'[\"p\"]')", AgentTokenScope.FUNCTIONAL_READ);
        jdbc.update("INSERT INTO project VALUES ('p',0,1),('other',0,1)");
        jdbc.update("INSERT INTO functional_case VALUES ('c','p',0),('other-case','other',0)");
        jdbc.update("INSERT INTO user VALUES ('u',1,0)");
        jdbc.update("INSERT INTO user_role_relation VALUES ('u','reader','p')");
        jdbc.update("INSERT INTO user_role VALUES ('reader',1,'PROJECT')");
        jdbc.update("INSERT INTO user_role_permission VALUES ('reader',?)", PermissionConstants.FUNCTIONAL_CASE_READ);
        var token = new AgentToken(); token.setId("t"); token.setUserId("u"); AgentTokenContext.set(token);
        environment = new MockEnvironment();
        configuration = new AgentSessionProtocolConfiguration(environment);
        var beans = new StaticListableBeanFactory();
        service = new AgentSessionProtocolService(new AgentBusinessAccess(jdbc), configuration,
                beans.getBeanProvider(AgentMcpToolRegistry.class));
        tool = new AgentSessionProtocolTools().sessionProtocolContract(service);
        beans.addBean("registry", new AgentMcpToolRegistry(List.of(tool)));
    }

    @AfterEach
    void cleanup() {
        AgentTokenContext.clear();
        jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void missingConfigurationIsExplicitAndExecutionRemainsUnavailable() {
        var result = service.contract(arguments());
        assertEquals(false, result.get("executionAvailable"));
        assertEquals(false, result.get("configurationReady"));
        assertEquals(AgentSessionProtocolService.REQUIRED_OPERATIONS, result.get("missingOperations"));
        assertTrue(configuration.executionReadiness().issues().contains(
                new AgentSessionProtocolConfiguration.Issue("retention.policy-version", "REQUIRED")));
        assertTrue(configuration.executionReadiness().issues().contains(
                new AgentSessionProtocolConfiguration.Issue("budget.calls", "REQUIRED")));
    }

    @Test
    void oldWriteSwitchDoesNotEnableNewProtocol() {
        environment.setProperty("agent.personal-execution.writes-enabled", "true");
        environment.setProperty("agent.mcp.business-writes-enabled", "true");
        assertFalse(configuration.executionReadiness().requested());
        assertEquals(false, service.contract(arguments()).get("executionAvailable"));
    }

    @Test
    void completeFixtureIsNotAnImplementationCertificateAndLeaksNoValues() throws Exception {
        configureFixture();
        assertTrue(configuration.executionReadiness().configurationReady());
        var result = service.contract(arguments());
        assertEquals(false, result.get("executionAvailable"));
        String encoded = new ObjectMapper().writeValueAsString(result);
        assertFalse(encoded.contains("fixture-confidential-reference"));
        assertFalse(encoded.contains("approval-record-value"));
        assertFalse(encoded.contains("exception"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.5", "1e3", "NaN", "9223372036854775808", "9999999999999999999999999"})
    void invalidBudgetsAreRejectedWithoutEchoingInput(String value) throws Exception {
        configureFixture();
        property("budget.calls", value);
        var readiness = configuration.executionReadiness();
        assertFalse(readiness.configurationReady());
        assertTrue(readiness.issues().stream().anyMatch(issue -> issue.key().equals("budget.calls")));
        assertFalse(new ObjectMapper().writeValueAsString(readiness).contains(value));
    }

    @Test
    void zeroBudgetsAreExplicitButZeroLengthLimitIsInvalid() {
        configureFixture();
        property("budget.recoveries", "0"); property("budget.calls", "0"); property("budget.recovery-seconds", "0");
        assertTrue(configuration.executionReadiness().configurationReady());
        property("limits.title-scalars", "0");
        assertFalse(configuration.executionReadiness().configurationReady());
    }

    @ParameterizedTest
    @ValueSource(strings = {"automatic-knowledge-publication", "persistent-client-cache"})
    void unsupportedPolicyCannotBeEnabledThroughConfiguration(String key) {
        configureFixture(); property(key, "true");
        assertTrue(configuration.executionReadiness().issues().contains(
                new AgentSessionProtocolConfiguration.Issue(key, "UNSUPPORTED_POLICY")));
        assertEquals(false, service.contract(arguments()).get("executionAvailable"));
    }

    @Test
    void malformedBooleanDoesNotSilentlyBecomeEnabled() {
        configureFixture(); property("enabled", "yes");
        assertFalse(configuration.executionReadiness().requested());
        assertTrue(configuration.executionReadiness().issues().contains(
                new AgentSessionProtocolConfiguration.Issue("enabled", "INVALID_BOOLEAN")));
    }

    @Test
    void automaticExpiryRequiresExplicitDeadlineAndApprovalAndIsNotEnabledByDefault() {
        configureFixture();
        assertTrue(configuration.executionReadiness().configurationReady());
        property("automatic-verification-expiry", "true");
        assertFalse(configuration.executionReadiness().configurationReady());
        property("verification.deadline-seconds", "10");
        assertFalse(configuration.executionReadiness().configurationReady());
        property("verification.deadline-approval", "fixture-only");
        assertTrue(configuration.executionReadiness().configurationReady());
        assertEquals(false, service.contract(arguments()).get("executionAvailable"));
    }

    @Test
    void unknownConfigurationIsRejectedWithoutExposingItsNameOrValue() throws Exception {
        configureFixture(); property("unknown-private-key", "private-value");
        var result = service.contract(arguments());
        assertEquals(false, result.get("configurationReady"));
        String json = new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("unknown-private-key"));
        assertFalse(json.contains("private-value"));
        assertTrue(json.contains("UNKNOWN_KEY"));
    }

    @Test
    void configurationSourceFailureIsClassifiedAndNeverLeaksRawMessage() throws Exception {
        configureFixture();
        environment.getPropertySources().addFirst(new org.springframework.core.env.PropertySource<>("failing") {
            @Override public Object getProperty(String name) {
                if (name.equals(AgentSessionProtocolConfiguration.PREFIX + "retention.policy-version"))
                    throw new IllegalStateException("private-credential-value");
                return null;
            }
        });
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AgentSessionProtocolService.class);
        var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        events.start(); logger.addAppender(events);
        try {
            var result = service.contract(arguments());
            assertEquals(false, result.get("configurationReady"));
            String json = new ObjectMapper().writeValueAsString(result);
            assertTrue(json.contains("CONFIGURATION_UNAVAILABLE"));
            assertFalse(json.contains("private-credential-value"));
            assertTrue(events.list.stream().anyMatch(event -> event.getFormattedMessage().contains("CONFIGURATION_UNAVAILABLE")));
            assertTrue(events.list.stream().allMatch(event -> event.getThrowableProxy() == null
                    && !event.getFormattedMessage().contains("private-credential-value")));
        } finally {
            logger.detachAppender(events); events.stop();
        }
    }

    @Test
    void restDoesNotIgnoreUnknownOrRepeatedParameters() {
        var controller = new AgentSessionProtocolController(service);
        var parameters = new org.springframework.util.LinkedMultiValueMap<String, String>();
        parameters.add("projectId", "p"); parameters.add("caseId", "c"); parameters.add("verified", "true");
        assertEquals("AGENT_VALIDATION_ERROR", assertThrows(MSException.class, () -> controller.contract(parameters)).getMessage());
        parameters.remove("verified"); parameters.add("caseId", "other-case");
        assertEquals("AGENT_VALIDATION_ERROR", assertThrows(MSException.class, () -> controller.contract(parameters)).getMessage());
    }

    @Test
    void liveRevocationIsCheckedEvenAfterSuccessfulDiscovery() {
        service.contract(arguments());
        jdbc.update("DELETE FROM user_role_permission");
        assertDenied(() -> tool.execute(arguments()));
        jdbc.update("INSERT INTO user_role_permission VALUES ('reader',?)", PermissionConstants.FUNCTIONAL_CASE_READ);
        service.contract(arguments());
        jdbc.update("UPDATE agent_token SET revoked_at=1");
        assertDenied(() -> service.contract(arguments()));
    }

    @Test
    void foreignMissingAndWrongProjectResourcesHaveSameDenial() {
        assertDenied(() -> tool.execute(Map.of("projectId", "other", "caseId", "other-case")));
        assertDenied(() -> tool.execute(Map.of("projectId", "p", "caseId", "missing")));
        assertDenied(() -> tool.execute(Map.of("projectId", "other", "caseId", "c")));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM functional_case", Integer.class));
    }

    @Test
    void unknownFieldsCannotSmugglePolicyOrVerification() {
        assertEquals("AGENT_VALIDATION_ERROR", assertThrows(MSException.class,
                () -> tool.execute(Map.of("projectId", "p", "caseId", "c", "verified", true))).getMessage());
    }

    @Test
    void restAndMcpShareResultsAndGatewayAlias() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AgentSessionProtocolController(service)).build();
        for (String prefix : List.of("/agent/v1", "/api/agent/v1")) {
            mvc.perform(get(prefix + "/execution-sessions/contract").accept(org.springframework.http.MediaType.APPLICATION_JSON)
                            .param("projectId", "p").param("caseId", "c"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.executionAvailable").value(false))
                    .andExpect(jsonPath("$.protocolVersion").value("1.0"));
        }
        assertEquals(false, ((Map<?, ?>) tool.execute(arguments())).get("executionAvailable"));
        assertTrue(tool.readOnlyHint());
        assertFalse(tool.destructiveHint());
        assertEquals(AgentTokenScope.FUNCTIONAL_READ, tool.requiredScope());
    }

    @Test
    void registryAndServiceConstructWithoutACircularDependency() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(AgentBusinessAccess.class, () -> new AgentBusinessAccess(jdbc));
            context.registerBean(AgentSessionProtocolConfiguration.class, () -> configuration);
            context.register(AgentSessionProtocolService.class, AgentSessionProtocolTools.class, AgentMcpToolRegistry.class);
            context.refresh();
            var handler = context.getBean(AgentMcpToolRegistry.class)
                    .find(AgentSessionProtocolService.TOOL_PREFIX + "contract").orElseThrow();
            assertEquals(false, ((Map<?, ?>) handler.execute(arguments())).get("executionAvailable"));
        }
    }

    private Map<String, Object> arguments() { return Map.of("projectId", "p", "caseId", "c"); }

    private void assertDenied(Runnable action) {
        assertEquals("AGENT_RESOURCE_NOT_ACCESSIBLE", assertThrows(MSException.class, action::run).getMessage());
    }

    private void property(String key, String value) {
        environment.setProperty(AgentSessionProtocolConfiguration.PREFIX + key, value);
    }

    private void configureFixture() {
        byte[] encryptionKey=new byte[32];new java.security.SecureRandom().nextBytes(encryptionKey);environment.setProperty("agent.execution.facts-key",java.util.Base64.getEncoder().encodeToString(encryptionKey));
        property("enabled", "true");
        for (String key : List.of("limits.title-scalars", "limits.body-scalars", "limits.reference-scalars",
                "limits.content-utf8-bytes", "limits.import-bytes", "limits.evidence-total-bytes", "lease-seconds")) property(key, "4");
        for (String key : List.of("budget.recoveries", "budget.calls", "budget.recovery-seconds")) property(key, "0");
        for (String key : List.of("approval.version", "retention.policy-version", "retention.approval-record",
                "retention.capacity-evidence", "retention.reference-protection-evidence", "identity.independence-evidence",
                "pilot.organization-id", "pilot.environment-id", "governance.responsibility-reference"))
            property(key, "fixture-confidential-reference");
        property("approval.record", "approval-record-value");
    }
}
