package io.metersphere.agent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "quality.mysql", matches = "true")
class AgentEnvironmentBindingMigrationMySqlTests {
    @Test void migrationMapsOnlyRealSameProjectAssetsWithoutChangingCredentialScopes() throws Exception {
        var admin = new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true", "root", "quality-test-only"));
        String schema = "environment_fix_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + schema);
        try {
            var ds = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/" + schema + "?useSSL=false&allowPublicKeyRetrieval=true", "root", "quality-test-only");
            var jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE ai_environment_execution_profile(id VARCHAR(64) PRIMARY KEY, project_id VARCHAR(64), environment_id VARCHAR(64)) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            jdbc.execute("CREATE TABLE environment(id VARCHAR(64) PRIMARY KEY, project_id VARCHAR(64)) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            jdbc.update("INSERT INTO environment VALUES ('real','p1'),('foreign','p2'),('CaseSensitive','p1')");
            jdbc.update("INSERT INTO ai_environment_execution_profile VALUES ('a','p1','real'),('b','p1','missing'),('c','p1','foreign'),('d','p1','casesensitive')");
            try (var connection = ds.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("migration/3.7.2/ddl/V3.7.2_105__environment_profile_asset_binding.sql"));
            }
            assertEquals("real", jdbc.queryForObject("SELECT environment_asset_id FROM ai_environment_execution_profile WHERE id='a'", String.class));
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM ai_environment_execution_profile WHERE environment_asset_id IS NULL", Integer.class));
            assertEquals("missing", jdbc.queryForObject("SELECT environment_id FROM ai_environment_execution_profile WHERE id='b'", String.class));
            assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM ai_environment_execution_profile", Integer.class));
        } finally {
            admin.execute("DROP DATABASE " + schema);
        }
    }
}
