package io.metersphere.agent.quality;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.service.AgentProjectService;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real SQL authorization with only the authenticated actor supplied by a session stub. */
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class GlobalQualityAccessMySqlTests {
    private final JdbcTemplate jdbc=new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only"));
    @BeforeEach void prepare() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_role(id VARCHAR(64) PRIMARY KEY,type VARCHAR(32),enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_role_relation(id VARCHAR(64) PRIMARY KEY,user_id VARCHAR(64),role_id VARCHAR(64),source_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_role_permission(id VARCHAR(64) PRIMARY KEY,role_id VARCHAR(64),permission_id VARCHAR(128))");
        jdbc.update("DELETE FROM user_role_relation WHERE user_id='qg-test-user'");
        jdbc.update("DELETE FROM user_role_permission WHERE id LIKE 'qg-test-%'");
        jdbc.update("INSERT INTO user_role(id,type,enabled) VALUES ('admin','SYSTEM',1) ON DUPLICATE KEY UPDATE type='SYSTEM',enabled=1");
        jdbc.update("INSERT INTO user_role(id,type,enabled) VALUES ('qg-test-project-admin','PROJECT',1),('qg-test-custom','SYSTEM',1) ON DUPLICATE KEY UPDATE enabled=1");
    }
    @AfterEach void clear() { AgentTokenContext.clear(); }
    @Test void pureAdminNeedsOnlySystemAssignmentAndRevocationIsImmediate() {
        try(var session=mockStatic(SessionUtils.class)) {
            session.when(SessionUtils::getUserId).thenReturn("qg-test-user");
            var projects=mock(AgentProjectService.class); var access=new QualityPolicyAccess(projects,jdbc);
            jdbc.update("INSERT INTO user_role_relation VALUES ('qg-test-admin','qg-test-user','admin','system')");
            for(String op:new String[]{"READ","MANAGE","PUBLISH"}) assertEquals("qg-test-user",access.requireSystem("SYSTEM_QUALITY:"+op));
            jdbc.update("UPDATE user_role SET enabled=0 WHERE id='admin'");
            assertThrows(MSException.class,()->access.requireSystem("SYSTEM_QUALITY:READ"));
            verifyNoInteractions(projects);
        }
    }
    @Test void projectGrantCannotPromoteAndSystemPermissionsAreIndependent() {
        try(var session=mockStatic(SessionUtils.class)) {
            session.when(SessionUtils::getUserId).thenReturn("qg-test-user");
            var access=new QualityPolicyAccess(mock(AgentProjectService.class),jdbc);
            jdbc.update("INSERT INTO user_role_relation VALUES ('qg-test-project','qg-test-user','qg-test-project-admin','project-a')");
            jdbc.update("INSERT INTO user_role_permission VALUES ('qg-test-project-read','qg-test-project-admin','SYSTEM_QUALITY:READ')");
            assertThrows(MSException.class,()->access.requireSystem("SYSTEM_QUALITY:READ"));
            jdbc.update("INSERT INTO user_role_relation VALUES ('qg-test-system','qg-test-user','qg-test-custom','system')");
            jdbc.update("INSERT INTO user_role_permission VALUES ('qg-test-read','qg-test-custom','SYSTEM_QUALITY:READ')");
            assertEquals("qg-test-user",access.requireSystem("SYSTEM_QUALITY:READ"));
            assertThrows(MSException.class,()->access.requireSystem("SYSTEM_QUALITY:MANAGE"));
            assertThrows(MSException.class,()->access.requireSystem("SYSTEM_QUALITY:PUBLISH"));
            jdbc.update("DELETE FROM user_role_relation WHERE id='qg-test-system'");
            assertThrows(MSException.class,()->access.requireSystem("SYSTEM_QUALITY:READ"));
        }
    }
    @Test void tokenOwnerAdminCannotManagePolicy() {
        try(var session=mockStatic(SessionUtils.class)) {
            session.when(SessionUtils::getUserId).thenReturn("qg-test-user");
            jdbc.update("INSERT INTO user_role_relation VALUES ('qg-test-admin','qg-test-user','admin','system')");
            AgentTokenContext.set(new AgentToken());
            var access=new QualityPolicyAccess(mock(AgentProjectService.class),jdbc);
            for(String op:new String[]{"READ","MANAGE","PUBLISH"})
                assertEquals("QUALITY_POLICY_FORBIDDEN",assertThrows(MSException.class,()->access.requireSystem("SYSTEM_QUALITY:"+op)).getMessage());
        }
    }
}
