package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.dto.AgentCaseSearchResponse;
import io.metersphere.agent.dto.AgentCaseSearchRequest;
import io.metersphere.agent.security.AgentFactCipher;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTestPlanContractLookupTests {
    @Test
    void selectsOnlyPublishedContractsForTheReturnedProjectAndCaseVersion() {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("""
                    CREATE TABLE msp_case_contract (
                        project_id VARCHAR(64), case_id VARCHAR(64), source_version VARCHAR(64),
                        state VARCHAR(32), version BIGINT
                    )
                    """);
            jdbc.update("""
                    INSERT INTO msp_case_contract VALUES
                    ('project','case','source','PUBLISHED',1),
                    ('project','case','source','PUBLISHED',2),
                    ('project','case','source','DRAFT',3),
                    ('project','case','old','PUBLISHED',4),
                    ('other','case','source','PUBLISHED',5),
                    ('project','stale','old','PUBLISHED',6)
                    """);
            var matching = new AgentCaseDTO();
            matching.setCaseId("case");
            matching.setVersionId("source");
            var stale = new AgentCaseDTO();
            stale.setCaseId("stale");
            stale.setVersionId("source");
            var response = new AgentCaseSearchResponse();
            response.setProjectId("project");
            response.setCases(List.of(matching, stale));
            var search = mock(AgentFunctionalCaseSearchService.class);
            when(search.search(any(AgentCaseSearchRequest.class))).thenReturn(response);
            var service = new AgentTestPlanQueryService();
            ReflectionTestUtils.setField(service, "agentFunctionalCaseSearchService", search);
            ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);

            service.cases("project", "plan", 1, 50, true);
            assertNull(matching.getStructuredContractVersion());
            service.cases("project", "plan", 1, 50, true, true);
            assertEquals(2L, matching.getStructuredContractVersion());
            assertNull(stale.getStructuredContractVersion());
        } finally {
            database.shutdown();
        }
    }
    @Test
    void readsOnlyThePublishedCurrentContractOfTheSpecifiedPlanRelation() {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE test_plan(id VARCHAR(64), project_id VARCHAR(64))");
            jdbc.execute("CREATE TABLE test_plan_functional_case(id VARCHAR(64), test_plan_id VARCHAR(64), functional_case_id VARCHAR(64))");
            jdbc.execute("CREATE TABLE functional_case(id VARCHAR(64), project_id VARCHAR(64), version_id VARCHAR(64), deleted INTEGER)");
            jdbc.execute("""
                    CREATE TABLE msp_case_contract (
                        project_id VARCHAR(64), case_id VARCHAR(64), source_version VARCHAR(64),
                        state VARCHAR(32), version BIGINT, content_hash VARCHAR(64), payload VARCHAR(2048)
                    )
                    """);
            jdbc.update("INSERT INTO test_plan VALUES ('plan','project'), ('other-plan','project')");
            jdbc.update("INSERT INTO test_plan_functional_case VALUES ('relation','plan','case'), ('other-relation','other-plan','case')");
            jdbc.update("INSERT INTO functional_case VALUES ('case','project','current',0)");
            jdbc.update("""
                    INSERT INTO msp_case_contract VALUES
                    ('project','case','current','PUBLISHED',1,'hash','encrypted'),
                    ('project','case','current','DRAFT',2,'draft','encrypted'),
                    ('project','case','old','PUBLISHED',3,'old','encrypted')
                    """);
            var projects = mock(AgentProjectService.class);
            when(projects.resolveProjectId("project")).thenReturn("project");
            var access = mock(AgentBusinessAccess.class);
            var cipher = new AgentFactCipher();
            byte[] key = new byte[32];
            new java.security.SecureRandom().nextBytes(key);
            ReflectionTestUtils.setField(cipher, "encodedKey", Base64.getEncoder().encodeToString(key));
            var store = new AgentProtocolStore(jdbc, new DataSourceTransactionManager(database), cipher);
            Map<String, Object> published = Map.of("goal", "verify state", "environmentId", "env",
                    "steps", List.of(Map.of("id", "S1", "sourcePosition", 1)),
                    "dataRequirements", List.of(Map.of("key", "order", "preparationMethodRef", "method:v2")));
            jdbc.update("UPDATE msp_case_contract SET payload = ? WHERE version = 1", store.payload(published));
            var service = new AgentTestPlanQueryService();
            ReflectionTestUtils.setField(service, "agentProjectService", projects);
            ReflectionTestUtils.setField(service, "access", access);
            ReflectionTestUtils.setField(service, "contractStore", store);
            ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);

            var result = service.readExecutionContract("project", "plan", "relation", 1L);
            assertEquals("relation", result.get("testPlanCaseId"));
            assertEquals(published, result.get("contract"));
            verify(access).plan("project", "plan");
            assertThrows(MSException.class,
                    () -> service.readExecutionContract("project", "plan", "other-relation", 1L));
            assertThrows(MSException.class,
                    () -> service.readExecutionContract("project", "plan", "relation", 2L));
            assertThrows(MSException.class,
                    () -> service.readExecutionContract("project", "plan", "relation", 3L));
            var task = new AgentExecutionTaskDTO();
            task.setProjectId("project");
            task.setTestPlanId("plan");
            task.setCreateUser("owner");
            task.setEnvironmentId("env");
            task.setId("task");
            task.setCurrentExecutionId("current-run");
            jdbc.execute("""
                    CREATE TABLE ai_execution_step_result (request_id VARCHAR(64), id VARCHAR(64), task_id VARCHAR(64),
                      execution_id VARCHAR(64), step_id VARCHAR(64), status VARCHAR(32),
                      facts_version VARCHAR(32), error_code VARCHAR(64), create_time BIGINT)
                    """);
            jdbc.execute("""
                    CREATE TABLE ai_execution_checkpoint (task_id VARCHAR(64), execution_id VARCHAR(64),
                      status VARCHAR(32), resumed_at BIGINT, checkpoint_version INTEGER)
                    """);
            jdbc.update("INSERT INTO ai_execution_checkpoint VALUES ('task','prior-run','RESUMED',10,1)");
            jdbc.update("""
                    INSERT INTO ai_execution_step_result (id,task_id,execution_id,step_id,status,facts_version,error_code,create_time) VALUES
                    ('receipt','task','current-run','runtime-step','SUCCESS','v1',NULL,1),
                    ('old','task','prior-run','runtime-step','SUCCESS','v1',NULL,2)
                    """);
            var assigned = new AgentExecutionCaseDTO();
            assigned.setCaseId("case");
            assigned.setTestPlanCaseId("relation");
            var runtimeStep = new AgentExecutionStepDTO();
            runtimeStep.setId("runtime-step");
            runtimeStep.setPos(10);
            assigned.setSteps(List.of(runtimeStep));
            task.setCases(List.of(assigned));
            task.setExecutionContract("""
                    {"cases":[{"caseId":"case","testPlanCaseId":"relation",
                    "structuredContractVersion":1,"structuredContractSourceVersion":"current"}]}
                    """);
            var token = new AgentToken();
            token.setUserId("other-user");
            AgentTokenContext.set(token);
            assertThrows(MSException.class, () -> service.readFrozenExecutionContract(task, "relation"));
            token.setUserId("owner");
            jdbc.update("UPDATE functional_case SET version_id = 'newer'");
            var frozen = service.readFrozenExecutionContract(task, "relation");
            assertEquals(1, ((Number) frozen.get("contractVersion")).intValue());
            assertEquals("current-run", frozen.get("executionId"));
            assertEquals(Map.of("S1", "runtime-step"), frozen.get("executionStepIds"));
            assertEquals(List.of("S1"), frozen.get("verifiedStepIds"));
            assertEquals("prior-run", frozen.get("resumedFromExecutionId"));
            assertEquals(List.of("S1"), frozen.get("previouslyVerifiedStepIds"));
            var protocols = mock(AgentProtocolAccess.class);
            when(protocols.organizationForProject("project")).thenReturn("org");
            var knowledge = mock(AgentKnowledgeService.class);
            token.setId("token");
            when(access.activeToken(io.metersphere.agent.constants.AgentTokenScope.FUNCTIONAL_READ)).thenReturn(token);
            var reader = new AgentProtocolAccess.Actor("org", "owner", "token", null, false);
            when(knowledge.fixed("org", "method", 2L, reader, true))
                    .thenReturn(Map.of("type", "DATA_PREPARATION", "preparation", Map.of("query", "read orders")));
            ReflectionTestUtils.setField(service, "protocolAccess", protocols);
            ReflectionTestUtils.setField(service, "knowledgeService", knowledge);
            var method = service.readDataPreparationMethod(task, "relation", "order");
            assertEquals(2L, method.get("version"));
            assertEquals(Map.of("query", "read orders"), method.get("preparation"));
            assertThrows(MSException.class, () -> service.readDataPreparationMethod(task, "relation", "other-data"));
            jdbc.update("""
                    INSERT INTO ai_execution_step_result (id,task_id,execution_id,step_id,status,facts_version,error_code,create_time) VALUES
                    ('blocked','task','current-run','runtime-step','BLOCKED','v1','WAITING_HUMAN',3)
                    """);
            var held = service.readFrozenExecutionContract(task, "relation");
            assertEquals(List.of(), held.get("verifiedStepIds"));
            assertEquals(Map.of("S1", "WAITING_HUMAN"), held.get("waitingSteps"));
            assertThrows(MSException.class, () -> service.readFrozenExecutionContract(task, "other-relation"));
            task.setEnvironmentId("different-env");
            assertThrows(MSException.class, () -> service.readFrozenExecutionContract(task, "relation"));
            task.setEnvironmentId("env");
            task.setExecutionContract("""
                    {"cases":[{"caseId":"case","steps":[{"stepId":"runtime-step",
                    "action":{"instruction":"Inspect status"},"assertions":[{"expected":"pending"}]}]}]}
                    """);
            var legacy = service.readFrozenExecutionContract(task, "relation");
            assertEquals(0, legacy.get("contractVersion"));
            assertEquals("TASK_LEGACY_SNAPSHOT", legacy.get("contractSource"));
            assertEquals(Map.of("runtime-step", "runtime-step"), legacy.get("executionStepIds"));
            assertEquals(Map.of("runtime-step", "WAITING_HUMAN"), legacy.get("waitingSteps"));
            jdbc.update("""
                    INSERT INTO ai_execution_step_result (request_id,id,task_id,execution_id,step_id,status,facts_version,error_code,create_time) VALUES
                    ('waiting-request','unknown','task','prior-run','runtime-step','BLOCKED','v1','WAITING_WRITE_RECONCILIATION',4)
                    """);
            var priorUnknown = service.readFrozenExecutionContract(task, "relation");
            assertEquals(Map.of("runtime-step", "WAITING_WRITE_RECONCILIATION"), priorUnknown.get("waitingSteps"));
            assertEquals(Map.of("runtime-step", Map.of("executionId", "prior-run", "requestId", "waiting-request",
                    "executionStepId", "runtime-step", "retryConsumed", false)), priorUnknown.get("writeRecoveryReceipts"));
            jdbc.update("DELETE FROM test_plan_functional_case WHERE id = 'relation'");
            assertThrows(MSException.class, () -> service.readFrozenExecutionContract(task, "relation"));
        } finally {
            AgentTokenContext.clear();
            database.shutdown();
        }
    }
}
