package io.metersphere.agent.service;

import io.metersphere.functional.dto.response.FunctionalCaseImportResponse;
import io.metersphere.functional.request.FunctionalCaseImportRequest;
import io.metersphere.functional.service.FunctionalLegacyImportService;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class AgentLegacyImportMySqlTests {
    @Test void taskEvidenceSurvivesBusinessRollbackAndRetainsAccurateSuccessCounts()throws Exception {
        String schema="legacy_import_"+UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only"));
        admin.execute("CREATE DATABASE "+schema);
        try {
            var ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only");
            var jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);
            try(var connection=ds.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_117__legacy_import_tracking.sql"));}
            jdbc.execute("CREATE TABLE project(id VARCHAR(50) PRIMARY KEY)");jdbc.update("INSERT INTO project VALUES ('p')");
            jdbc.execute("CREATE TABLE business_write(id VARCHAR(50))");
            var service=new FunctionalLegacyImportService(jdbc,manager);
            var request=new FunctionalCaseImportRequest();request.setProjectId("p");
            var file=new MockMultipartFile("file","test.xls","application/octet-stream",new byte[]{1,2,3});
            assertThrows(MSException.class,()->service.run(request,"user",file,()->new TransactionTemplate(manager).execute(status->{
                jdbc.update("INSERT INTO business_write VALUES ('rolled-back')");
                FunctionalLegacyImportService.stage("WRITING",100);
                throw new IllegalStateException("expected rollback");
            })));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM business_write",Integer.class));
            assertEquals("FAILED",service.recent("p","user").getFirst().get("state"));
            var response=service.run(request,"user",file,()->{
                var result=new FunctionalCaseImportResponse();result.setSuccessCount(12);result.setFailCount(2);return result;
            });
            assertNotNull(response.getJobId());
            assertEquals(14,jdbc.queryForObject("SELECT processed_rows FROM functional_legacy_import_job WHERE id=?",Integer.class,response.getJobId()));
            assertEquals("PARTIAL_FAILED",jdbc.queryForObject("SELECT state FROM functional_legacy_import_job WHERE id=?",String.class,response.getJobId()));
            assertTrue(service.recent("p","other-user").isEmpty());
            jdbc.update("UPDATE functional_legacy_import_job SET state='RUNNING',heartbeat_at=0 WHERE id=?",response.getJobId());
            service.heartbeat();
            assertEquals("INTERRUPTED",jdbc.queryForObject("SELECT state FROM functional_legacy_import_job WHERE id=?",String.class,response.getJobId()));
        } finally {admin.execute("DROP DATABASE "+schema);}
    }
}
