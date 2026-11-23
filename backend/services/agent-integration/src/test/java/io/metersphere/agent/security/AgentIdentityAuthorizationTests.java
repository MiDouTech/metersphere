package io.metersphere.agent.security;

import io.metersphere.agent.service.AgentBusinessAccess;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class AgentIdentityAuthorizationTests {
    private final java.util.List<org.springframework.jdbc.datasource.SingleConnectionDataSource> mysqlSources = new java.util.ArrayList<>();
    private final java.util.List<String> mysqlSchemas = new java.util.ArrayList<>();
    @AfterEach void clear() {
        AgentTokenContext.clear();
        mysqlSources.forEach(org.springframework.jdbc.datasource.SingleConnectionDataSource::destroy);
        if(!mysqlSchemas.isEmpty()) {
            var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only"));
            for(String schema:mysqlSchemas) if(schema.matches("mcp_identity_[a-f0-9]{32}"))admin.execute("DROP DATABASE "+schema);
        }
    }

    @Test void allAndOldTokensNeverRequireFeatureSpecificScopeUpgrades() {
        for (String existing : new String[]{"AGENT_ALL", "FUNCTIONAL_READ", "BUG_WRITE", "", null}) {
            for (String operation : new String[]{"BUSINESS_ASSET_WRITE", "SHARED_CONFIGURATION_RESOLVE",
                    "CASE_DELETE_V2", "PROJECT_FILE_DOWNLOAD", "FUTURE_USER_ROLE_CONTROLLED_OPERATION"}) {
                assertTrue(AgentTokenScopeParser.hasScope(existing, operation),
                        "Historical scope metadata must not impose an independent feature grant");
            }
        }
    }

    @Test void authenticatedTokenWithoutLegacyScopeStillReachesRoleChecks() {
        AgentToken token = new AgentToken(); token.setId("token"); token.setUserId("actor");
        AgentTokenContext.set(token);
        assertDoesNotThrow(() -> AgentScopeAssert.assertScope("SHARED_CONFIGURATION_RESOLVE"));
        AgentTokenContext.clear();
        assertThrows(MSException.class, () -> AgentScopeAssert.assertScope("SHARED_CONFIGURATION_RESOLVE"));
    }

    @Test void oldTokenCanUseCurrentRoleButRevocationAndProjectBoundaryStillDeny() {
        JdbcTemplate db = database();
        AgentToken token = new AgentToken(); token.setId("token"); token.setUserId("actor");
        AgentTokenContext.set(token);
        AgentBusinessAccess access = new AgentBusinessAccess(db);
        assertEquals("actor", access.project("project", "SHARED_CONFIGURATION:RESOLVE", true, "SHARED_CONFIGURATION_RESOLVE"));
        assertThrows(MSException.class, () -> access.project("other", "SHARED_CONFIGURATION:RESOLVE", true, "SHARED_CONFIGURATION_RESOLVE"));
        db.update("DELETE FROM user_role_permission");
        assertThrows(MSException.class, () -> access.project("project", "SHARED_CONFIGURATION:RESOLVE", true, "SHARED_CONFIGURATION_RESOLVE"));
        db.update("INSERT INTO user_role_permission VALUES('reader','SHARED_CONFIGURATION:RESOLVE')");
        db.update("UPDATE agent_token SET enable=FALSE");
        assertThrows(MSException.class, () -> access.project("project", "SHARED_CONFIGURATION:RESOLVE", true, "SHARED_CONFIGURATION_RESOLVE"));
        db.update("UPDATE agent_token SET enable=TRUE,expire_time=1");
        assertThrows(MSException.class, () -> access.project("project", "SHARED_CONFIGURATION:RESOLVE", true, "SHARED_CONFIGURATION_RESOLVE"));
    }

    private JdbcTemplate database() {
        javax.sql.DataSource source;
        if (Boolean.getBoolean("quality.mysql")) {
            var mysql = new org.springframework.jdbc.datasource.SingleConnectionDataSource(
                    "jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true", "root", "quality-test-only", true);
            mysqlSources.add(mysql); source = mysql;
        } else source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate db = new JdbcTemplate(source);
        if(Boolean.getBoolean("quality.mysql")) {
            String schema="mcp_identity_"+UUID.randomUUID().toString().replace("-","");
            db.execute("CREATE DATABASE "+schema);mysqlSchemas.add(schema);db.execute("USE "+schema);
        }
        String create = "CREATE TABLE ";
        db.execute(create + "agent_token(id VARCHAR(128) PRIMARY KEY,user_id VARCHAR(128),enable BOOLEAN,status VARCHAR(128),revoked_at BIGINT,expire_time BIGINT,scopes VARCHAR(128),project_ids VARCHAR(128),project_id VARCHAR(128))");
        db.execute(create + "project(id VARCHAR(128) PRIMARY KEY,deleted INT,enable INT,organization_id VARCHAR(128))");
        db.execute(create + "user(id VARCHAR(128) PRIMARY KEY,enable INT,deleted INT)");
        db.execute(create + "user_role(id VARCHAR(128) PRIMARY KEY,enabled INT,type VARCHAR(128))");
        db.execute(create + "user_role_relation(user_id VARCHAR(128),role_id VARCHAR(128),source_id VARCHAR(128))");
        db.execute(create + "user_role_permission(role_id VARCHAR(128),permission_id VARCHAR(128))");
        db.update("INSERT INTO agent_token VALUES('token','actor',TRUE,'ACTIVE',NULL,NULL,'FUNCTIONAL_READ','[\"project\"]','project')");
        db.update("INSERT INTO project VALUES('project',0,1,'org'),('other',0,1,'org')");
        db.update("INSERT INTO user VALUES('actor',1,0)");
        db.update("INSERT INTO user_role VALUES('reader',1,'PROJECT')");
        db.update("INSERT INTO user_role_relation VALUES('actor','reader','project')");
        db.update("INSERT INTO user_role_permission VALUES('reader','SHARED_CONFIGURATION:RESOLVE')");
        return db;
    }

    @Test void projectOrganizationAndSystemRolesFollowCurrentWebInheritance() {
        var db=database(); var access=new AgentBusinessAccess(db);
        db.update("DELETE FROM user_role_relation");
        db.update("INSERT INTO user_role VALUES('project_admin',1,'PROJECT'),('org_admin',1,'ORGANIZATION'),('custom-system',1,'SYSTEM')");
        db.update("INSERT INTO user_role_relation VALUES('actor','project_admin','project')");
        assertTrue(access.userHasPermission("actor","project","ANY_NEW_PERMISSION"));
        assertFalse(access.userHasPermission("actor","other","ANY_NEW_PERMISSION"));
        db.update("DELETE FROM user_role_relation");
        db.update("INSERT INTO user_role_relation VALUES('actor','org_admin','org')");
        assertTrue(access.userHasPermission("actor","project","ANY_NEW_PERMISSION"));
        db.update("UPDATE project SET organization_id='other-org' WHERE id='other'");
        assertFalse(access.userHasPermission("actor","other","ANY_NEW_PERMISSION"));
        db.update("DELETE FROM user_role_relation");
        db.update("INSERT INTO user_role_relation VALUES('actor','custom-system','system')");
        db.update("INSERT INTO user_role_permission VALUES('custom-system','ANY_NEW_PERMISSION')");
        assertTrue(access.userHasPermission("actor","project","ANY_NEW_PERMISSION"));
        db.update("UPDATE user_role SET enabled=0 WHERE id='custom-system'");
        assertFalse(access.userHasPermission("actor","project","ANY_NEW_PERMISSION"));
        db.update("UPDATE user_role SET enabled=1");
        db.update("UPDATE user SET enable=0");
        assertFalse(access.userHasPermission("actor","project","ANY_NEW_PERMISSION"));
    }

    @Test void configurationMcpKeepsEncryptedHistoryAndPinsSessionWhileRecheckingCurrentRoles() throws Exception {
        var db = database();
        db.update("INSERT INTO user_role_permission VALUES('reader','SHARED_CONFIGURATION:READ'),('reader','SHARED_CONFIGURATION:WRITE')");
        var root = java.nio.file.Path.of("").toAbsolutePath();
        while (!java.nio.file.Files.exists(root.resolve("backend/framework/domain"))) root = root.getParent();
        var migrations = root.resolve("backend/framework/domain/src/main/resources/migration/3.7.2/ddl");
        String sql = java.nio.file.Files.readString(migrations.resolve("V3.7.2_152__shared_configurations.sql"));
        for (String statement : sql.substring(0, sql.indexOf("INSERT IGNORE")).split(";"))
            if (!statement.isBlank()) db.execute(statement);
        for(String statement:java.nio.file.Files.readString(migrations.resolve("V3.7.2_157__shared_configuration_secret_type.sql")).split(";"))
            if(!statement.isBlank())db.execute(statement);
        db.execute("CREATE TABLE "
                + "external_executor_session(id VARCHAR(128),project_id VARCHAR(128),token_id VARCHAR(128),actor_id VARCHAR(128),status VARCHAR(128))");
        AgentToken token = new AgentToken(); token.setId("token"); token.setUserId("actor"); token.setScopes("FUNCTIONAL_READ");
        AgentTokenContext.set(token);
        var service = new io.metersphere.agent.service.SharedConfigurationService(db,
                new io.metersphere.agent.service.BusinessAssetService(db, new AgentBusinessAccess(db)),
                new SharedConfigurationCipher(java.util.Base64.getEncoder().encodeToString(new byte[32])));
        var tools = new io.metersphere.agent.tool.configuration.SharedConfigurationMcpTools(service,
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());
        String payload = "{\"fields\":[{\"name\":\"username\",\"value\":\"synthetic-user\"},{\"name\":\"password\",\"value\":\"synthetic-private-value\"}]}";
        var args = new java.util.LinkedHashMap<String,Object>(java.util.Map.of("projectId","project","systemKey","system",
                "environmentKey","test","name","synthetic-account","kind","SECRET","secretType","ACCOUNT_PASSWORD","value",payload,"expectedVersion",0));
        var saved = (java.util.Map<?,?>)tools.configurationSave().execute(args);
        String id = (String)saved.get("id");
        assertFalse(tools.configurationList().execute(java.util.Map.of("projectId","project")).toString().contains("synthetic-private-value"));
        assertEquals("******", ((java.util.Map<?,?>)tools.configurationRead().execute(java.util.Map.of("projectId","project","id",id,"version",1))).get("value"));
        String encrypted = db.queryForObject("SELECT encrypted_value FROM shared_configuration_version WHERE configuration_id=?", String.class, id);
        assertFalse(encrypted.contains("synthetic-private-value"));
        db.update("INSERT INTO external_executor_session VALUES('session','project','token','actor','RUNNING')");
        db.update("INSERT INTO external_executor_configuration VALUES('session',?,1)",id);
        args.put("id",id);args.put("expectedVersion",1);args.put("value",payload.replace("synthetic-private-value","replacement-fixture"));
        tools.configurationSave().execute(args);
        var resolve = java.util.Map.<String,Object>of("projectId","project","id",id,"sessionId","session");
        var pinned = (java.util.Map<?,?>)tools.configurationResolve().execute(resolve);
        assertEquals(1,pinned.get("version"));assertEquals(payload,pinned.get("value"));
        db.update("DELETE FROM user_role_permission WHERE permission_id='SHARED_CONFIGURATION:RESOLVE'");
        assertThrows(MSException.class, () -> tools.configurationResolve().execute(resolve));
        db.update("INSERT INTO user_role_permission VALUES('reader','SHARED_CONFIGURATION:RESOLVE')");
        db.update("UPDATE external_executor_session SET status='COMPLETED'");
        assertThrows(MSException.class, () -> tools.configurationResolve().execute(resolve));
    }
}
