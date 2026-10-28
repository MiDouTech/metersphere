package io.metersphere.agent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Dedicated disposable local database only; never silently use an application datasource. */
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class WeeklyRemediationMigrationMySqlTests {
    @Test void staleProjectionUsesTheRealOutboxQueryAndDoesNotDuplicatePendingOrFailedWork() throws Exception {
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only"));
        String schema="weekly_outbox_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE DATABASE "+schema);
        try {
            var ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only");
            var jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE functional_case(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),ref_id VARCHAR(50),deleted BOOLEAN,latest BOOLEAN,update_user VARCHAR(50))");
            jdbc.execute("CREATE TABLE case_asset_source_relation(id VARCHAR(50) PRIMARY KEY,source_project_id VARCHAR(50),source_case_id VARCHAR(50),source_update_time BIGINT)");
            try(var connection=ds.getConnection()) {
                ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_119__functional_import_effect_outbox.sql"));
                ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_136__asset_execution_projection_reconcile.sql"));
            }
            jdbc.update("INSERT INTO functional_case VALUES ('stale','p','ref-stale',0,1,'u'),('fresh','p','ref-fresh',0,1,'u'),('failed','p','ref-failed',0,1,'u')");
            jdbc.update("INSERT INTO case_asset_source_relation VALUES ('r1','p','ref-stale',0),('r2','p','ref-fresh',100),('r3','p','ref-failed',0)");
            jdbc.update("INSERT INTO functional_import_effect_outbox(id,effect_type,project_id,user_id,payload,state,next_retry_at,create_time,update_time) VALUES ('failed-work','CASE_SYNC','p','u','{\"caseId\":\"failed\"}','FAILED',1,1,1)");
            var manager=new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds);
            var outbox=new io.metersphere.functional.service.FunctionalImportEffectOutbox(jdbc,manager,null,null,null,null);
            var transaction=new org.springframework.transaction.support.TransactionTemplate(manager);
            transaction.executeWithoutResult(status->outbox.reconcileStaleProjection());
            transaction.executeWithoutResult(status->outbox.reconcileStaleProjection());
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_effect_outbox",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_effect_outbox WHERE state='PENDING' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.caseId'))='stale'",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_effect_outbox WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$.caseId'))='fresh'",Integer.class));
            assertEquals("FAILED",jdbc.queryForObject("SELECT state FROM functional_import_effect_outbox WHERE id='failed-work'",String.class));
        } finally {admin.execute("DROP DATABASE "+schema);}
    }

    @Test void ambiguousLegacyJobsPauseEvenWhenTheOldBugAlreadyWroteAnItem() throws Exception {
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only"));
        String schema="weekly_fix_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE DATABASE "+schema);
        try {
            var ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only");
            var jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE functional_import_job(id VARCHAR(50) PRIMARY KEY,state VARCHAR(32),update_time BIGINT)");
            jdbc.execute("CREATE TABLE functional_import_followup(job_id VARCHAR(50),policy VARCHAR(32),source_kind VARCHAR(16))");
            jdbc.execute("CREATE TABLE functional_import_item(job_id VARCHAR(50),state VARCHAR(32))");
            jdbc.execute("CREATE TABLE case_asset_source_relation(id VARCHAR(50) PRIMARY KEY,source_update_time BIGINT)");
            jdbc.update("INSERT INTO functional_import_job VALUES ('ambiguous','RUNNING',10),('confirmed','QUEUED',20),('precheck','PRECHECKED',30)");
            jdbc.update("INSERT INTO functional_import_item VALUES ('ambiguous','SUCCESS')");
            jdbc.update("INSERT INTO functional_import_followup VALUES ('confirmed','IMPORT_ONLY','FILE')");
            jdbc.update("INSERT INTO case_asset_source_relation VALUES ('asset',123)");
            try(var connection=ds.getConnection()) {
                for(String name:new String[]{"V3.7.2_135__import_commit_stage.sql","V3.7.2_136__asset_execution_projection_reconcile.sql"})
                    ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/"+name));
            }
            assertEquals("STOPPED",jdbc.queryForObject("SELECT state FROM functional_import_job WHERE id='ambiguous'",String.class));
            assertNull(jdbc.queryForObject("SELECT committed_at FROM functional_import_job WHERE id='ambiguous'",Long.class));
            assertEquals("QUEUED",jdbc.queryForObject("SELECT state FROM functional_import_job WHERE id='confirmed'",String.class));
            assertEquals(20L,jdbc.queryForObject("SELECT committed_at FROM functional_import_job WHERE id='confirmed'",Long.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_item WHERE state='SUCCESS'",Integer.class));
            assertEquals(0L,jdbc.queryForObject("SELECT source_update_time FROM case_asset_source_relation",Long.class));
        } finally {admin.execute("DROP DATABASE "+schema);}
    }
}
