package io.metersphere.agent.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class AgentRecoveryScanMySqlTests {
    @Test void migrationCoordinatesInstancesAndPersistsFailureAndRecovery() throws Exception {
        String schema="recovery_scan_"+UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only"));
        admin.execute("CREATE DATABASE "+schema);
        try {
            var ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only");
            try(var connection=ds.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_116__recovery_scan_health.sql"));}
            var jdbc=new JdbcTemplate(ds);
            var first=new AgentRecoveryScanHealth(jdbc);var second=new AgentRecoveryScanHealth(jdbc);
            assertTrue(first.begin());assertFalse(second.begin());
            assertTrue(first.failure(new SQLException("collation","HY000",1267)));
            assertEquals("STRUCTURE",first.status().getFirst().get("errorCategory"));
            assertFalse(second.begin());
            jdbc.update("UPDATE agent_recovery_scan_health SET lease_until=0,next_scan_at=0");
            assertTrue(second.begin());second.success();
            assertEquals(0,((Number)second.status().getFirst().get("consecutiveFailures")).intValue());
            assertNotNull(second.status().getFirst().get("lastSuccessAt"));
        } finally {admin.execute("DROP DATABASE "+schema);}
    }
}
