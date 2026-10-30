package io.metersphere.system.base;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "quality.mysql", matches = "true")
class OrganizationFixtureMySqlTests {
    @Test void springSqlFixtureCanBeExecutedTwiceWithoutTouchingOtherIdentities() throws Exception {
        var admin = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true", "root", "quality-test-only"));
        String schema = "fixture_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + schema);
        try {
            var ds = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/" + schema
                    + "?useSSL=false&allowPublicKeyRetrieval=true", "root", "quality-test-only");
            var jdbc = new JdbcTemplate(ds);
            String ddl = new ClassPathResource("migration/3.0.0/ddl/V3.0.0_11__system_setting.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            for (String table : List.of("organization", "user", "user_role", "user_role_relation", "user_role_permission")) {
                var matcher = Pattern.compile("CREATE TABLE IF NOT EXISTS " + table + "\\s*\\(.*?;", Pattern.DOTALL).matcher(ddl);
                assertTrue(matcher.find(), table);
                jdbc.execute(matcher.group());
            }
            jdbc.update("INSERT INTO organization(id,name,create_time,update_time,create_user,update_user) VALUES('keep','keep',1,1,'admin','admin')");
            try (var connection = ds.getConnection()) {
                var resource = new EncodedResource(new ClassPathResource("dml/init_sys_organization.sql"), StandardCharsets.UTF_8);
                ScriptUtils.executeSqlScript(connection, resource);
                ScriptUtils.executeSqlScript(connection, resource);
            }
            assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM organization", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM organization WHERE id='keep'", Integer.class));
            assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM user", Integer.class));
            assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM user_role_relation", Integer.class));
            String departmentDdl = new ClassPathResource("migration/3.7.0/ddl/V3.7.0_1__org_structure.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            var department = Pattern.compile("CREATE TABLE IF NOT EXISTS department\\s*\\(.*?;", Pattern.DOTALL).matcher(departmentDdl);
            assertTrue(department.find());
            jdbc.execute(department.group());
            jdbc.execute("ALTER TABLE user ADD COLUMN cft_token VARCHAR(255) NOT NULL DEFAULT 'NONE'");
            try (var connection = ds.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource(
                        "migration/3.7.0/ddl/V3.7.0_2__user_org_structure.sql"), StandardCharsets.UTF_8));
                var resource = new EncodedResource(new ClassPathResource("dml/init_org_structure_test_data.sql"), StandardCharsets.UTF_8);
                ScriptUtils.executeSqlScript(connection, resource);
                ScriptUtils.executeSqlScript(connection, resource);
            }
            assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM department", Integer.class));
            assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id LIKE 'org_structure_user_%'", Integer.class));
        } finally {
            admin.execute("DROP DATABASE " + schema);
        }
    }
}
