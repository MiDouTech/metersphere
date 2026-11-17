package io.metersphere.agent.service;

import io.metersphere.functional.planreview.PlanReviewFailure;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImportCommitConfirmationTests {
    private void exercise(String confirmation, boolean allowed) {
        var source=new DriverManagerDataSource("jdbc:h2:mem:commit_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);
        try {
            jdbc.execute("CREATE TABLE functional_import_job(id VARCHAR(50),user_id VARCHAR(50),token_id VARCHAR(50),project_id VARCHAR(50),state VARCHAR(32),source_name VARCHAR(100),expires_at BIGINT,committed_at BIGINT,stopped_from VARCHAR(32),update_time BIGINT)");
            jdbc.execute("CREATE TABLE project(id VARCHAR(50),deleted BOOLEAN,enable BOOLEAN)");
            jdbc.execute("CREATE TABLE functional_import_item(job_id VARCHAR(50),position INT,action VARCHAR(32),target_id VARCHAR(50),state VARCHAR(32),message VARCHAR(100))");
            jdbc.execute("CREATE TABLE plan_review_import_followup(job_id VARCHAR(50))");
            jdbc.execute("CREATE TABLE functional_import_followup(job_id VARCHAR(50))");
            jdbc.update("INSERT INTO project VALUES('project',false,true)");
            jdbc.update("INSERT INTO functional_import_job(id,user_id,token_id,project_id,state,source_name,expires_at) VALUES('job','user','WEB','project','PRECHECKED','fixture.xlsx',?)",System.currentTimeMillis()+60000);
            jdbc.update("INSERT INTO functional_import_item VALUES('job',1,'CREATE',NULL,'PENDING',NULL)");
            if(confirmation!=null)jdbc.update("INSERT INTO "+confirmation+" VALUES('job')");
            var access=mock(AgentBusinessAccess.class);
            when(access.userHasPermission(anyString(),anyString(),anyString())).thenReturn(true);
            var service=new AgentCaseImportService();
            ReflectionTestUtils.setField(service,"jdbc",jdbc);
            ReflectionTestUtils.setField(service,"access",access);
            ReflectionTestUtils.setField(service,"transactionManager",new DataSourceTransactionManager(source));
            if(allowed) {
                assertEquals("QUEUED",service.action("job","commit","user","WEB").get("state"));
                long committed=jdbc.queryForObject("SELECT committed_at FROM functional_import_job",Long.class);
                assertTrue(committed>0);
                service.action("job","commit","user","WEB");
                assertEquals(committed,jdbc.queryForObject("SELECT committed_at FROM functional_import_job",Long.class));
            } else {
                var failure=assertThrows(PlanReviewFailure.class,()->service.action("job","commit","user","WEB"));
                assertEquals("IMPORT_FOLLOWUP_REQUIRED",failure.code());
                assertEquals("PRECHECKED",jdbc.queryForObject("SELECT state FROM functional_import_job",String.class));
                assertNull(jdbc.queryForObject("SELECT committed_at FROM functional_import_job",Long.class));
            }
            assertEquals("PENDING",jdbc.queryForObject("SELECT state FROM functional_import_item",String.class));
        } finally { jdbc.execute("SHUTDOWN"); }
    }
    @Test void currentConfirmationAllowsCommitAndReplayWithoutChangingCommitTime(){exercise("plan_review_import_followup",true);}
    @Test void legacyConfirmationRemainsCompatible(){exercise("functional_import_followup",true);}
    @Test void unconfirmedImportCannotQueueOrWrite(){exercise(null,false);}
}
