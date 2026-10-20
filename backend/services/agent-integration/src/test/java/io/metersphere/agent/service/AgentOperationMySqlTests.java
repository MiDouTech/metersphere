package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class AgentOperationMySqlTests {
    private final String schema="mcp_fix_"+UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate jdbc,admin;private AgentOperationExecutor operations;private AgentBusinessAccess access;
    private AgentToken token;
    @BeforeEach void setup() throws Exception {
        admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only"));
        admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4");
        var ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only");
        jdbc=new JdbcTemplate(ds);operations=new AgentOperationExecutor(jdbc,new DataSourceTransactionManager(ds));access=new AgentBusinessAccess(jdbc);
        try(var connection=ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_21__agent_temp_attachment_idempotency.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_101__agent_operation_idempotency.sql"));
        }
        jdbc.execute("CREATE TABLE effect(id VARCHAR(64) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE agent_token(id VARCHAR(64),user_id VARCHAR(64),enable BOOLEAN,status VARCHAR(32),scopes TEXT,project_ids TEXT,revoked_at BIGINT,expire_time BIGINT)");
        jdbc.execute("CREATE TABLE user(id VARCHAR(64),enable BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE project(id VARCHAR(64),enable BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role(id VARCHAR(64),enabled BOOLEAN,type VARCHAR(32))");
        jdbc.execute("CREATE TABLE user_role_relation(user_id VARCHAR(64),role_id VARCHAR(64),source_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE user_role_permission(role_id VARCHAR(64),permission_id VARCHAR(64))");
        jdbc.update("INSERT INTO agent_token VALUES ('t','u',1,'ACTIVE','REVIEW_WRITE','[\"p\"]',NULL,NULL)");
        jdbc.update("INSERT INTO user VALUES ('u',1,0)");jdbc.update("INSERT INTO project VALUES ('p',1,0),('foreign',1,0)");
        jdbc.update("INSERT INTO user_role VALUES ('writer',1,'PROJECT')");jdbc.update("INSERT INTO user_role_relation VALUES ('u','writer','p')");jdbc.update("INSERT INTO user_role_permission VALUES ('writer','review:create')");
        token=new AgentToken();token.setId("t");token.setUserId("u");AgentTokenContext.set(token);
    }
    @AfterEach void cleanup(){AgentTokenContext.clear();if(admin!=null && schema.matches("mcp_fix_[a-f0-9]{32}"))admin.execute("DROP DATABASE "+schema);}
    private void authorize(){access.project("p","review:create",true,"REVIEW_WRITE");}
    private Map write(String key,Map<String,Object> args){return operations.execute("case_review.create",key,args,Map.class,this::authorize,()->{jdbc.update("INSERT INTO effect VALUES (?)",UUID.randomUUID().toString());return Map.of("id","created");});}

    @Test void canonicalReplayAndChangedPayloadConflictAndRevocation() {
        Map<String,Object> args=Map.of("name","review","nested",Map.of("b",2,"a",1));
        assertEquals(write("request1",args),write("request1",Map.of("nested",Map.of("a",1,"b",2),"name","review")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM effect",Integer.class));
        assertThrows(MSException.class,()->write("request1",Map.of("name","changed")));
        jdbc.update("UPDATE user_role SET enabled=0");
        assertThrows(MSException.class,()->write("request1",args));
    }
    @Test void rollbackIncludesEffectAndReservation() {
        assertThrows(IllegalStateException.class,()->operations.execute("case_review.create","rollback",Map.of(),Map.class,this::authorize,()->{jdbc.update("INSERT INTO effect VALUES ('partial')");throw new IllegalStateException("rollback");}));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM effect",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM agent_idempotency_record",Integer.class));
        write("rollback",Map.of());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM effect",Integer.class));
    }
    @Test void concurrentRequestsCommitOneEffect() throws Exception {
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Map> call=()->{AgentTokenContext.set(token);try{start.await();return write("concurrent",Map.of("name","review"));}finally{AgentTokenContext.clear();}};
            var first=pool.submit(call);var second=pool.submit(call);start.countDown();
            assertEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM effect",Integer.class));
        }finally{pool.shutdownNow();}
    }
    @Test void deniesForeignProjectDisabledUserAndRevokedToken() {
        assertThrows(MSException.class,()->access.project("foreign","review:create",true,"REVIEW_WRITE"));
        jdbc.update("UPDATE user SET enable=0");assertThrows(MSException.class,this::authorize);
        jdbc.update("UPDATE user SET enable=1");jdbc.update("UPDATE agent_token SET revoked_at=1");assertThrows(MSException.class,this::authorize);
    }

    @Test void commentsMustBelongToTheSameCaseAndReplyAuthor() {
        jdbc.execute("CREATE TABLE functional_case_comment(id VARCHAR(64),case_id VARCHAR(64),create_user VARCHAR(64))");
        jdbc.update("INSERT INTO functional_case_comment VALUES ('comment','case','author')");
        assertDoesNotThrow(()->access.commentParent("case","comment","author"));
        assertThrows(MSException.class,()->access.commentParent("foreign","comment","author"));
        assertThrows(MSException.class,()->access.commentParent("case","comment","other-user"));
    }

    @Test void legacyKeysRequireReconciliationAndDoNotRepeatEffects() {
        jdbc.update("INSERT INTO agent_idempotency_record(id,token_id,tool_name,request_id,request_hash,create_time,status,hash_version) VALUES ('old','t','metersphere.case_review.create','old-key','legacy',1,'UNKNOWN','legacy-v0')");
        assertThrows(MSException.class,()->write("old-key",Map.of()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM effect",Integer.class));
    }

    @Test void attachmentConsumptionIsExclusiveAndRollsBackWithTheOperation() {
        long future=System.currentTimeMillis()+60000;
        jdbc.update("INSERT INTO agent_temp_attachment(id,token_id,user_id,project_id,file_id,purpose,linked,expires_at) VALUES ('attachment','t','u','p','storage','CASE_COMMENT',0,?)",future);
        var attachment=new io.metersphere.system.domain.AgentTempAttachment();
        attachment.setId("attachment");attachment.setProjectId("p");attachment.setPurpose("CASE_COMMENT");
        var service=new AgentTempAttachmentService();
        org.springframework.test.util.ReflectionTestUtils.setField(service,"jdbc",jdbc);
        assertThrows(IllegalStateException.class,()->operations.execute("functional.comment.create","attachment-rollback",Map.of(),Map.class,this::authorize,()->{
            service.markLinked(List.of(attachment));throw new IllegalStateException("rollback");
        }));
        assertEquals(0,jdbc.queryForObject("SELECT linked FROM agent_temp_attachment WHERE id='attachment'",Integer.class));
        service.markLinked(List.of(attachment));
        assertThrows(MSException.class,()->service.markLinked(List.of(attachment)));
        jdbc.update("UPDATE agent_temp_attachment SET linked=0,token_id='another-token'");
        assertThrows(MSException.class,()->service.markLinked(List.of(attachment)));
        assertEquals(0,jdbc.queryForObject("SELECT linked FROM agent_temp_attachment WHERE id='attachment'",Integer.class));
    }

    @Test void temporaryDownloadsRequireOwnershipPurposeScopeAndLiveReference() {
        long future = System.currentTimeMillis() + 60000;
        jdbc.update("INSERT INTO agent_temp_attachment(id,token_id,user_id,project_id,file_id,purpose,linked,expires_at) VALUES ('attachment','t','u','p','storage','CASE_COMMENT',0,?)", future);
        var service = new AgentTempAttachmentService();
        var projects = org.mockito.Mockito.mock(AgentProjectService.class);
        org.mockito.Mockito.when(projects.resolveProjectId("p")).thenReturn("p");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "jdbc", jdbc);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "agentProjectService", projects);
        token.setScopes("FUNCTIONAL_READ");
        assertDoesNotThrow(() -> service.requireDownload("p", "storage"));
        assertDoesNotThrow(() -> service.requireMetadata("attachment"));
        assertThrows(MSException.class, () -> service.requireDownload("p", "unknown"));
        token.setId("other");
        assertThrows(MSException.class, () -> service.requireDownload("p", "storage"));
        assertThrows(MSException.class, () -> service.requireMetadata("attachment"));
        token.setId("t");
        token.setScopes("BUG_READ");
        assertThrows(MSException.class, () -> service.requireDownload("p", "storage"));
        token.setScopes("FUNCTIONAL_READ");
        jdbc.update("UPDATE agent_temp_attachment SET linked=1");
        assertThrows(MSException.class, () -> service.requireDownload("p", "storage"));
        jdbc.update("UPDATE agent_temp_attachment SET linked=0,expires_at=1");
        assertThrows(MSException.class, () -> service.requireDownload("p", "storage"));
        jdbc.update("UPDATE agent_temp_attachment SET expires_at=?,purpose='EXECUTION'", future);
        assertDoesNotThrow(() -> service.requireDownload("p", "storage"));
        token.setProjectIds("[\"foreign\"]");
        assertThrows(MSException.class, () -> service.requireDownload("p", "storage"));
    }
}
