package io.metersphere.agent.quality;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

@EnabledIfSystemProperty(named="quality.mysql", matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GlobalQualityPolicyMySqlTests {
    private AnnotationConfigApplicationContext context;
    private GlobalQualityPolicyService service;
    private JdbcTemplate jdbc;
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true", "root", "quality-test-only");
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager tx(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean QualityPolicyValidator validator() { return new QualityPolicyValidator(); }
        @Bean GlobalQualityGate gate(JdbcTemplate jdbc, QualityPolicyValidator validator) {
            return new GlobalQualityGate(jdbc, mock(io.metersphere.agent.mapper.AgentExecutionMapper.class), validator);
        }
        @Bean QualityPolicyAccess access() {
            var access = mock(QualityPolicyAccess.class);
            when(access.requireSystem(anyString())).thenReturn("system-admin");
            return access;
        }
        @Bean GlobalQualityPolicyService service(JdbcTemplate jdbc, QualityPolicyAccess access, QualityPolicyValidator validator) {
            var ids = mock(io.metersphere.system.uid.impl.DefaultUidGenerator.class);
            var sequence = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
            when(ids.getUID()).thenAnswer(call -> sequence.incrementAndGet());
            return new GlobalQualityPolicyService(jdbc, access, validator, ids);
        }
    }
    @BeforeAll void migrate() {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(GlobalQualityPolicyService.class);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='quality_verify' AND table_name='execution_quality_policy'", Integer.class) == 0)
            new ResourceDatabasePopulator(new ClassPathResource("migration/3.7.2/ddl/V3.7.2_91__execution_quality_policy.sql")).execute(context.getBean(DataSource.class));
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='quality_verify' AND table_name='execution_quality_policy_global'", Integer.class) == 0)
            new ResourceDatabasePopulator(new ClassPathResource("migration/3.7.2/ddl/V3.7.2_95__global_quality_policy.sql")).execute(context.getBean(DataSource.class));
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='quality_verify' AND table_name='execution_quality_attempt_policy'", Integer.class) == 0)
            new ResourceDatabasePopulator(new ClassPathResource("migration/3.7.2/ddl/V3.7.2_97__quality_attempt_binding.sql")).execute(context.getBean(DataSource.class));
    }
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM execution_quality_attempt_policy");
        jdbc.update("UPDATE execution_quality_policy_global_state SET current_policy_id=NULL,next_version=1,row_version=0");
        jdbc.update("DELETE FROM execution_quality_policy_global_publication");
        jdbc.update("DELETE FROM execution_quality_policy_global");
    }
    @AfterAll void close() { if (context != null) context.close(); }
    private GlobalQualityPolicyService.Policy draft() {
        return service.create(new GlobalQualityPolicyService.DraftRequest(QualityPolicyValidatorTests.VALID, null));
    }
    private GlobalQualityPolicyService.PublishRequest publish(GlobalQualityPolicyService.Policy policy, String current) {
        return new GlobalQualityPolicyService.PublishRequest(policy.rowVersion(), current, "global change");
    }
    @Test void globalLifecycleAndImmutableHistory() {
        var first = draft();
        assertEquals("SYSTEM", first.scope());
        var published = service.publish(first.id(), publish(first,null));
        assertEquals(first.id(), service.list().currentPolicyId());
        assertThrows(Exception.class, () -> service.update(first.id(),new GlobalQualityPolicyService.DraftRequest(first.rulesJson(),published.rowVersion())));
        var next = draft();
        service.publish(next.id(),publish(next,first.id()));
        assertEquals(next.id(),service.list().currentPolicyId());
        assertEquals("PUBLISHED",service.detail(first.id()).policy().status());
        assertEquals(first.id(),service.detail(next.id()).publication().previousPolicyId());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM execution_quality_policy_global_publication WHERE trace_id IS NOT NULL",Integer.class));
    }
    @Test void staleDraftDoesNotOverwriteAndSingletonCannotExpand() {
        var policy = draft();
        service.update(policy.id(),new GlobalQualityPolicyService.DraftRequest(policy.rulesJson(),0L));
        assertThrows(Exception.class, () -> service.update(policy.id(),new GlobalQualityPolicyService.DraftRequest(policy.rulesJson(),0L)));
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO execution_quality_policy_global_state(scope) VALUES ('project-a')"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM execution_quality_policy_global_state",Integer.class));
    }
    @Test void concurrentPublishHasOneWinner() throws Exception {
        var a=draft(); var b=draft();
        var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            java.util.function.Function<GlobalQualityPolicyService.Policy,Callable<Boolean>> attempt=p -> () -> {
                barrier.await();
                try { service.publish(p.id(),publish(p,null)); return true; }
                catch(io.metersphere.sdk.exception.MSException conflict) { assertEquals("QUALITY_POLICY_VERSION_CONFLICT",conflict.getMessage()); return false; }
            };
            var x=pool.submit(attempt.apply(a)); var y=pool.submit(attempt.apply(b));
            assertNotEquals(x.get(15,TimeUnit.SECONDS),y.get(15,TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM execution_quality_policy_global_publication",Integer.class));
    }
    @Test void invalidRulesNeverAllocateVersion() {
        assertThrows(QualityPolicyValidationException.class, () -> service.create(new GlobalQualityPolicyService.DraftRequest("{}",null)));
        assertEquals(0,service.list().total());
        assertNull(service.list().currentPolicyId());
    }
    @Test void attemptsAcrossProjectsBindOneVersionAndInFlightSnapshotSurvivesPublication() {
        var gate=context.getBean(GlobalQualityGate.class);
        assertThrows(io.metersphere.sdk.exception.MSException.class,()->gate.bind("a","project-a-task"));
        var policy=draft();service.publish(policy.id(),publish(policy,null));
        var a=gate.bind("a","project-a-task");var b=gate.bind("b","project-b-task");
        assertEquals(a.policyId(),b.policyId());assertEquals(a.contentHash(),b.contentHash());
        var next=draft();service.publish(next.id(),publish(next,policy.id()));
        assertEquals(policy.id(),gate.binding("a","project-a-task").policyId());
        assertEquals(next.id(),gate.bind("c","new-project-task").policyId());
        assertThrows(io.metersphere.sdk.exception.MSException.class,()->gate.binding("a","project-b-task"));
        jdbc.update("UPDATE execution_quality_attempt_policy SET content_hash=REPEAT('0',64) WHERE execution_id='a'");
        assertThrows(io.metersphere.sdk.exception.MSException.class,()->gate.binding("a","project-a-task"));
    }
    @Test void formalWritebackRechecksLatestResultAndEvidenceScope() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_execution_step_result (id VARCHAR(50) PRIMARY KEY, execution_id VARCHAR(50), step_id VARCHAR(50), status VARCHAR(20), assertion_result TEXT, artifact_ids TEXT, create_time BIGINT)");
        jdbc.update("DELETE FROM ai_execution_step_result WHERE execution_id='gate-e2e'");
        var policy=draft(); service.publish(policy.id(),publish(policy,null));
        var mapper=mock(io.metersphere.agent.mapper.AgentExecutionMapper.class);
        var gate=new GlobalQualityGate(jdbc,mapper,context.getBean(QualityPolicyValidator.class));
        gate.bind("gate-e2e","gate-task");
        var task=new io.metersphere.agent.dto.AgentExecutionTaskDTO();task.setId("gate-task");task.setCurrentExecutionId("gate-e2e");
        String contract="{\"cases\":[{\"steps\":[{\"stepId\":\"step\",\"assertions\":[{\"operator\":\"EQUALS\",\"expected\":\"ok\"}]}]}]}";
        task.setExecutionContract(contract);task.setExecutionContractHash(org.apache.commons.codec.digest.DigestUtils.sha256Hex(contract));
        var item=new io.metersphere.agent.dto.AgentExecutionCaseDTO();item.setStatus("SUCCESS");
        var step=new io.metersphere.agent.dto.AgentExecutionStepDTO();step.setId("step");step.setStatus("SUCCESS");
        var evidence=new io.metersphere.agent.dto.AgentExecutionArtifactDTO();evidence.setTaskId(task.getId());evidence.setExecutionId("gate-e2e");evidence.setStepId("step");
        evidence.setStatus("AVAILABLE");evidence.setPurpose("AFTER_STEP");evidence.setContentType("image/png");evidence.setSizeBytes(10L);evidence.setSha256("a".repeat(64));
        when(mapper.selectArtifactById("proof")).thenReturn(evidence);
        var steps=java.util.List.of(step);
        assertThrows(io.metersphere.sdk.exception.MSException.class,()->gate.requireCase(task,item,steps));
        jdbc.update("INSERT INTO ai_execution_step_result VALUES ('gate-result-1','gate-e2e','step','SUCCESS',?, ?,1)","[{\"actual\":\"ok\"}]","[\"proof\"]");
        assertDoesNotThrow(()->gate.requireCase(task,item,steps));
        evidence.setStepId("other-step");
        assertThrows(io.metersphere.sdk.exception.MSException.class,()->gate.requireCase(task,item,steps));
        evidence.setStepId("step");
        jdbc.update("INSERT INTO ai_execution_step_result VALUES ('gate-result-2','gate-e2e','step','FAILED','[]','[]',2)");
        assertThrows(io.metersphere.sdk.exception.MSException.class,()->gate.requireCase(task,item,steps));
        jdbc.update("DELETE FROM ai_execution_step_result WHERE execution_id='gate-e2e'");
    }

}
