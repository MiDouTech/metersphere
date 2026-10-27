package io.metersphere.agent.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import io.metersphere.functional.mapper.ExtFunctionalCaseMapper;
import io.metersphere.functional.request.FunctionalCasePageRequest;
import io.metersphere.functional.service.FunctionalImportEffectOutbox;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.functional.hub.service.DefaultHubCaseSyncService;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.service.CommonNoticeSendService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="quality.mysql", matches="true")
class SqlStabilityMySqlTests {
    private final String schema = "sql_stability_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate jdbc, admin;
    private DriverManagerDataSource ds;
    @BeforeEach void setup() {
        admin = new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true", "root", "quality-test-only"));
        admin.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        ds = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/" + schema + "?useSSL=false&allowPublicKeyRetrieval=true", "root", "quality-test-only");
        jdbc = new JdbcTemplate(ds);
    }
    @AfterEach void cleanup() {
        PageHelper.clearPage();
        if (schema.matches("sql_stability_[a-f0-9]{32}")) admin.execute("DROP DATABASE " + schema);
    }
    private org.apache.ibatis.session.SqlSessionFactory factory() throws Exception {
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setPlugins(new PageInterceptor());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:io/metersphere/**/mapper/*.xml"));
        return factory.getObject();
    }

    @Test void planOverviewAggregatesSharedPlansOnceWithoutChangingDeletedOrDuplicateRowSemantics() throws Exception {
        jdbc.execute("CREATE TABLE functional_case(id VARCHAR(64) PRIMARY KEY,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE test_plan(id VARCHAR(64) PRIMARY KEY,num BIGINT,name VARCHAR(64),status VARCHAR(32),update_time BIGINT)");
        jdbc.execute("CREATE TABLE test_plan_functional_case(id VARCHAR(64) PRIMARY KEY,functional_case_id VARCHAR(64),test_plan_id VARCHAR(64),last_exec_result VARCHAR(32),KEY by_case(functional_case_id),KEY by_plan(test_plan_id))");
        jdbc.update("INSERT INTO functional_case VALUES ('a',0),('b',0),('gone',1)");
        jdbc.update("INSERT INTO test_plan VALUES ('p',1,'shared','ARCHIVED',1),('empty',2,'empty','RUNNING',2)");
        jdbc.update("INSERT INTO test_plan_functional_case VALUES ('1','a','p','SUCCESS'),('2','b','p','PENDING'),('3','gone','p','SUCCESS'),('4','a','p','SUCCESS'),('5','gone','empty','SUCCESS')");
        try (var session=factory().openSession()) {
            var mapper=session.getMapper(ExtFunctionalCaseMapper.class);
            var rows=mapper.getTestPlanOverviewByCaseIds(List.of("a","b","gone"));
            assertEquals(4,rows.size());
            for (var row:rows) {
                var baseline=mapper.getTestPlanOverview(row.getCaseId()).stream().filter(p->p.getId().equals(row.getId())).findFirst().orElseThrow();
                assertEquals(baseline.getTotal(),row.getTotal());
                assertEquals(baseline.getExecuted(),row.getExecuted());
                assertEquals(baseline.getPassed(),row.getPassed());
                assertEquals(baseline.getRate(),row.getRate());
                assertEquals(baseline.getArchived(),row.getArchived());
            }
        }
    }

    @Test void lightBodyProjectionDoesNotLoadStepBlobs() throws Exception {
        jdbc.execute("CREATE TABLE functional_case_blob(id VARCHAR(64) PRIMARY KEY,steps LONGBLOB,text_description LONGBLOB,expected_result LONGBLOB,prerequisite LONGBLOB,description LONGBLOB)");
        jdbc.update("INSERT INTO functional_case_blob VALUES ('a','[]','text','expected','pre','description')");
        try (var session=factory().openSession()) {
            var mapper=session.getMapper(ExtFunctionalCaseMapper.class);
            var light=mapper.getAgentContent(List.of("a"),false).getFirst();
            assertNull(light.getSteps());
            assertNull(light.getTextDescription());
            assertEquals("pre",new String(light.getPrerequisite(),java.nio.charset.StandardCharsets.UTF_8));
            assertNotNull(mapper.getAgentContent(List.of("a"),true).getFirst().getSteps());
        }
    }

    @Test void jdbcDeadlineAndPoolExhaustionRecoverAfterKilledConnection() throws Exception {
        try (var pool = new com.zaxxer.hikari.HikariDataSource()) {
            pool.setJdbcUrl(ds.getUrl()); pool.setUsername("root"); pool.setPassword("quality-test-only");
            pool.setMaximumPoolSize(1); pool.setMinimumIdle(0); pool.setConnectionTimeout(300);
            pool.setPoolName("sql-stability-fault-test");
            long connectionId;
            try (var held=pool.getConnection(); var statement=held.createStatement()) {
                try(var result=statement.executeQuery("SELECT CONNECTION_ID()")) { result.next(); connectionId=result.getLong(1); }
                assertThrows(java.sql.SQLTransientConnectionException.class, pool::getConnection);
                statement.setQueryTimeout(1);
                assertThrows(java.sql.SQLException.class, ()->statement.executeQuery("SELECT SLEEP(5)"));
                try(var result=statement.executeQuery("SELECT 1")) { assertTrue(result.next()); }
                admin.execute("KILL CONNECTION "+connectionId);
                assertThrows(java.sql.SQLException.class, ()->statement.executeQuery("SELECT 1"));
            }
            try(var fresh=pool.getConnection(); var statement=fresh.createStatement(); var result=statement.executeQuery("SELECT 1")) {
                assertTrue(result.next()); assertEquals(1,result.getInt(1));
            }
        }
    }

    @Test void effectsRollbackWithBusinessAndFailedEffectsRetryWithoutLosingCommittedRows() throws Exception {
        try(var c=ds.getConnection()) {
            ScriptUtils.executeSqlScript(c,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_119__functional_import_effect_outbox.sql"));
            ScriptUtils.executeSqlScript(c,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_127__review_recount_queue.sql"));
            ScriptUtils.executeSqlScript(c,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_128__asset_sync_failure_detail.sql"));
        }
        var hub=mock(DefaultHubCaseSyncService.class);
        var cases=mock(FunctionalCaseService.class);
        var manager=new DataSourceTransactionManager(ds);
        var outbox=new FunctionalImportEffectOutbox(jdbc,manager,hub,cases,mock(UserMapper.class),mock(CommonNoticeSendService.class));
        jdbc.execute("CREATE TABLE case_review_functional_case(review_id VARCHAR(50),case_id VARCHAR(50))");
        org.springframework.test.util.ReflectionTestUtils.setField(outbox,"reviewRecount",new io.metersphere.functional.service.FunctionalReviewRecountService(jdbc,manager));
        var tx=new TransactionTemplate(manager);
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
            outbox.enqueue("p","u",List.of("rolled-back"),List.of(),"CREATE");
            throw new IllegalStateException("rollback");
        }));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_effect_outbox",Integer.class));
        tx.executeWithoutResult(status->outbox.enqueue("p","u",List.of("case"),List.of(),"CREATE"));
        doThrow(new IllegalStateException("dependency unavailable")).when(hub).syncCaseUpsert("p","case","u");
        outbox.drain();
        assertEquals(1,jdbc.queryForObject("SELECT attempts FROM functional_import_effect_outbox",Integer.class));
        assertEquals("PENDING",jdbc.queryForObject("SELECT state FROM functional_import_effect_outbox",String.class));
        assertTrue(jdbc.queryForObject("SELECT next_retry_at>update_time FROM functional_import_effect_outbox",Boolean.class));
        reset(hub);
        jdbc.update("UPDATE functional_import_effect_outbox SET next_retry_at=0");
        outbox.drain();outbox.drain();
        assertEquals("SUCCESS",jdbc.queryForObject("SELECT state FROM functional_import_effect_outbox",String.class));
        verify(hub,times(1)).syncCaseUpsert("p","case","u");
        verify(cases,times(1)).publishImportedCaseAsset("case","u");
    }
}
