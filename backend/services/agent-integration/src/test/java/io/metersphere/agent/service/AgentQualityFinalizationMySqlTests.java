package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.quality.GlobalQualityGate;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class AgentQualityFinalizationMySqlTests {
    private final String schema="quality_fix_"+UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate admin,jdbc;private DriverManagerDataSource ds;private DataSourceTransactionManager transactions;
    @BeforeEach void database() throws Exception {
        admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only"));
        admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only");jdbc=new JdbcTemplate(ds);transactions=new DataSourceTransactionManager(ds);
        jdbc.execute("CREATE TABLE ai_execution_task(id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64),current_execution_id VARCHAR(64),status VARCHAR(32)) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        jdbc.execute("CREATE TABLE ai_execution_attempt(id VARCHAR(64) PRIMARY KEY,task_id VARCHAR(64),status VARCHAR(32),update_time BIGINT,version INT)");
        jdbc.execute("CREATE TABLE ai_execution_case(id VARCHAR(64),task_id VARCHAR(64),case_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE ai_execution_step_result(id VARCHAR(64) PRIMARY KEY)");
        try(var connection=ds.getConnection()){
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_98__quality_step_facts.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_99__quality_finalization.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_100__quality_policy_trial.sql"));
        }
        jdbc.update("INSERT INTO ai_execution_task VALUES ('t1','p1','e1','WRITING_BACK')");jdbc.update("INSERT INTO ai_execution_attempt VALUES ('e1','t1','RUNNING',0,0)");jdbc.update("INSERT INTO ai_execution_case VALUES ('ec1','t1','c1')");
    }
    @AfterEach void cleanup(){if(admin!=null && schema.matches("quality_fix_[a-f0-9]{32}"))admin.execute("DROP DATABASE "+schema);}
    @SuppressWarnings("unchecked") private <T> T transactional(T service){var factory=new ProxyFactory(service);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();}
    @Test void finalFactsSurviveRestartBlockFurtherFactsAndFenceOldAttempts(){
        var mapper=mock(AgentExecutionMapper.class);var gate=mock(GlobalQualityGate.class);
        var task=new AgentExecutionTaskDTO();task.setId("t1");task.setProjectId("p1");task.setCurrentExecutionId("e1");task.setStatus("WRITING_BACK");
        var c=new AgentExecutionCaseDTO();c.setId("ec1");c.setCaseId("c1");c.setStatus("SUCCESS");
        when(mapper.selectTaskById("t1")).thenReturn(task);when(mapper.selectCasesByTaskId("t1")).thenReturn(List.of(c));when(mapper.selectStepsByTaskId("t1")).thenReturn(List.of());
        var service=transactional(new AgentExecutionFinalizationService(jdbc,mapper,gate));
        assertEquals("SUCCESS",service.prepare("t1").getFirst().executionCase().getStatus());
        assertEquals("FINALIZING",jdbc.queryForObject("SELECT status FROM ai_execution_attempt WHERE id='e1'",String.class));
        c.setStatus("FAILED");
        var restarted=transactional(new AgentExecutionFinalizationService(jdbc,mapper,gate));
        assertEquals("SUCCESS",restarted.prepare("t1").getFirst().executionCase().getStatus());
        var tx=new TransactionTemplate(transactions);
        assertThrows(MSException.class,()->tx.execute(status->{restarted.requireMutable("t1","e1");return null;}));
        assertTrue(restarted.recoverableTasks().contains("t1"));
        try(var connection=ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_118__writeback_identifier_collations.sql"));
        } catch(Exception error) { throw new AssertionError("Identifier migration failed",error); }
        assertTrue(restarted.recoverableTasks().contains("t1"));
        assertEquals("utf8mb4_general_ci",jdbc.queryForObject("SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_execution_case_sync' AND COLUMN_NAME='task_id'",String.class));
        var lease=new AgentRunnerLeaseDTO();lease.setTaskId("t1");lease.setExecutionId("e1");lease.setId("l1");lease.setRunnerId("r1");lease.setVersion(0);
        assertTrue(restarted.reconcileExpiredFinalization(lease));
        verify(mapper,never()).closeRunnerLease(anyString(),anyString(),anyInt(),anyString(),anyLong());
        task.setStatus("SUCCESS");task.setVerdict("PASSED");
        assertTrue(restarted.reconcileExpiredFinalization(lease));
        verify(mapper).closeRunnerLease(eq("l1"),eq("r1"),eq(0),eq("COMPLETED"),anyLong());
        verify(mapper).finishExecutionAttempt(eq("e1"),eq("SUCCEEDED"),isNull(),isNull(),anyLong());
        jdbc.update("UPDATE ai_execution_task SET current_execution_id='e2' WHERE id='t1'");
        assertThrows(MSException.class,()->tx.execute(status->{restarted.beginSync(task,c);return null;}));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_execution_case_sync WHERE status='PENDING'",Integer.class));
    }
    @Test void claimRechecksRevokedActorDeletedCaseAndDisabledEnvironment() {
        jdbc.execute("CREATE TABLE project(id VARCHAR(64),organization_id VARCHAR(64),deleted BOOLEAN,enable BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role(id VARCHAR(64),type VARCHAR(32),enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role_relation(user_id VARCHAR(64),role_id VARCHAR(64),source_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE functional_case(id VARCHAR(64),project_id VARCHAR(64),deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE ai_environment_execution_profile(id VARCHAR(64),project_id VARCHAR(64),environment_id VARCHAR(64),enabled BOOLEAN,environment_type VARCHAR(32))");
        jdbc.execute("CREATE TABLE ai_credential_reference(id VARCHAR(64),project_id VARCHAR(64),environment_id VARCHAR(64),enabled BOOLEAN,status VARCHAR(32),last_verify_status VARCHAR(32),expires_at BIGINT)");
        jdbc.update("INSERT INTO project VALUES ('p1','o1',0,1)");
        jdbc.update("INSERT INTO user_role VALUES ('member','PROJECT',1)");
        jdbc.update("INSERT INTO user_role_relation VALUES ('u1','member','p1')");
        jdbc.update("INSERT INTO functional_case VALUES ('c1','p1',0)");
        jdbc.update("INSERT INTO ai_environment_execution_profile VALUES ('env1','p1','test1',1,'TEST')");
        jdbc.update("INSERT INTO ai_credential_reference VALUES ('cred1','p1','test1',1,'ACTIVE','PASSED',NULL)");
        var task=new AgentExecutionTaskDTO();task.setId("t1");task.setProjectId("p1");task.setCreateUser("u1");task.setEnvironmentProfileId("env1");task.setCredentialReferenceId("cred1");
        var safety=new AgentExecutionClaimSafetyService(jdbc);
        assertDoesNotThrow(()->safety.requireClaimable(task));
        jdbc.update("UPDATE user_role SET enabled=0");
        assertThrows(MSException.class,()->safety.requireClaimable(task));
        jdbc.update("UPDATE user_role SET enabled=1");
        jdbc.update("UPDATE functional_case SET deleted=1");
        assertThrows(MSException.class,()->safety.requireClaimable(task));
        jdbc.update("UPDATE functional_case SET deleted=0");
        jdbc.update("UPDATE ai_environment_execution_profile SET enabled=0");
        assertThrows(MSException.class,()->safety.requireClaimable(task));
        jdbc.update("UPDATE ai_environment_execution_profile SET enabled=1");
        jdbc.update("UPDATE ai_credential_reference SET expires_at=1");
        assertThrows(MSException.class,()->safety.requireClaimable(task));
    }
    @Test void rejectedAuditCommitsEvenWhenBusinessTransactionRollsBack(){
        var service=transactional(new AgentQualityAuditService(jdbc));var request=new AgentExecutionStepSubmitRequest();request.setTaskId("t1");request.setExecutionId("e1");request.setStepId("s1");request.setRequestId("r1");
        try(var ids=mockStatic(IDGenerator.class)){
            ids.when(IDGenerator::nextStr).thenReturn("audit1");
            assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{jdbc.queryForList("SELECT id FROM ai_execution_task WHERE id='t1' FOR UPDATE");service.rejected(request,"QUALITY_ASSERTION_FAILED","trace1");throw new IllegalStateException("rollback");}));
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_quality_evaluation WHERE trace_id='trace1'",Integer.class));
    }
}
