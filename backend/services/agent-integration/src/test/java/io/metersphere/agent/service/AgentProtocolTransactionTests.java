package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentFactCipher;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises production SQL and transactions; H2 evidence is explicitly not MySQL acceptance. */
class AgentProtocolTransactionTests {
    JdbcTemplate jdbc;AgentProtocolStore store;AgentProtocolEvidenceService evidence;
    private JdbcTemplate mysqlAdmin;private String mysqlSchema;
    protected boolean mysql(){return false;}
    final AgentProtocolAccess.Actor actor=new AgentProtocolAccess.Actor("org","user","agent","person",false);
    @BeforeEach void setup() throws Exception {
        DriverManagerDataSource ds;
        if(mysql()) {
            String password=Objects.requireNonNull(System.getenv("PROTOCOL_MYSQL_PASSWORD"),"Isolated fixture credential required");
            String options="?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=10000";
            mysqlAdmin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/quality_verify"+options,"root",password));mysqlSchema="msp33_protocol_"+UUID.randomUUID().toString().replace("-","");
            assertTrue(mysqlSchema.matches("msp33_protocol_[a-f0-9]{32}"));mysqlAdmin.execute("CREATE DATABASE "+mysqlSchema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+mysqlSchema+options,"root",password);
        }else ds=new DriverManagerDataSource("jdbc:h2:mem:protocol_"+UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=USER;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");
        jdbc=new JdbcTemplate(ds);var cipher=new AgentFactCipher();byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);ReflectionTestUtils.setField(cipher,"encodedKey",Base64.getEncoder().encodeToString(key));
        store=new AgentProtocolStore(jdbc,new DataSourceTransactionManager(ds),cipher);evidence=new AgentProtocolEvidenceService(jdbc,store);
        jdbc.execute("CREATE TABLE agent_case_evidence(id VARCHAR(64) PRIMARY KEY,token_id VARCHAR(64),project_id VARCHAR(64),case_id VARCHAR(64),submission_id VARCHAR(64),expires_at BIGINT,sha256 VARCHAR(64),content_type VARCHAR(64),size_bytes BIGINT,payload BLOB)");
        jdbc.execute("CREATE TABLE ai_execution_step_result(id VARCHAR(64) PRIMARY KEY,lease_id VARCHAR(50))");
        jdbc.execute("CREATE TABLE agent_case_submission(id VARCHAR(64) PRIMARY KEY,token_id VARCHAR(64) NOT NULL)");
        try(var input=getClass().getResourceAsStream("/migration/3.7.2/ddl/V3.7.2_148__governed_execution_protocol.sql")) {
            assertNotNull(input);String script=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            if(!mysql()) {
                script=script.replace(" COLLATE utf8mb4_bin","").replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","");
                var matcher=java.util.regex.Pattern.compile("ALTER TABLE ([a-z_]+) ([^;]+);").matcher(script);var converted=new StringBuffer();
                while(matcher.find()){String table=matcher.group(1);String[] clauses=matcher.group(2).split(",\\s*\\n\\s*");matcher.appendReplacement(converted,java.util.regex.Matcher.quoteReplacement(Arrays.stream(clauses).map(clause->"ALTER TABLE "+table+" "+clause+";").collect(java.util.stream.Collectors.joining("\n"))));}matcher.appendTail(converted);script=converted.toString();
            }
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(script.getBytes(StandardCharsets.UTF_8))).execute(ds);
        }
        jdbc.execute("CREATE TABLE effects(id VARCHAR(64) PRIMARY KEY)");jdbc.execute("CREATE TABLE meter(id INT PRIMARY KEY,used INT)");jdbc.update("INSERT INTO meter VALUES (1,0)");
        jdbc.execute("CREATE TABLE ai_execution_task(id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE ai_execution_artifact(id VARCHAR(64) PRIMARY KEY,task_id VARCHAR(64),status VARCHAR(32),retention_until BIGINT,sha256 VARCHAR(64),content_type VARCHAR(64),size_bytes BIGINT,execution_case_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE ai_execution_case(id VARCHAR(64) PRIMARY KEY,task_id VARCHAR(64),case_id VARCHAR(64))");jdbc.update("INSERT INTO ai_execution_case VALUES ('source-case','task','case')");
        jdbc.update("INSERT INTO ai_execution_task VALUES ('task','project')");jdbc.update("INSERT INTO ai_execution_artifact VALUES ('image','task','AVAILABLE',1,'hash','image/png',128,'source-case')");
        jdbc.execute("CREATE TABLE agent_case_submission_evidence(submission_id VARCHAR(64),evidence_id VARCHAR(64),retain_until BIGINT)");
        jdbc.update("INSERT INTO agent_case_evidence(id,project_id,case_id,expires_at,sha256,content_type,size_bytes,payload) VALUES ('personal','project','case',1,'hash','image/png',1,?)",new byte[]{1});
    }
    @AfterEach void cleanup(){if(mysqlAdmin!=null){assertTrue(mysqlSchema.matches("msp33_protocol_[a-f0-9]{32}"));mysqlAdmin.execute("DROP DATABASE "+mysqlSchema);}else if(jdbc!=null)jdbc.execute("DROP ALL OBJECTS");}
    Map<String,Object> args(){return Map.of("idempotencyKey","same","expectedContextVersion",7,"leaseGeneration",2,"value",Map.of("b",2,"a",1));}
    Runnable meter(){return ()->jdbc.update("UPDATE meter SET used=used+1 WHERE id=1");}
    Map<String,Object> write(Map<String,Object> args,AgentProtocolStore.Authorization auth,java.util.function.Function<AgentProtocolStore.WriteContext,Map<String,Object>> action){return store.execute("step.save","instance",args,auth,meter(),action);}
    @Test void concurrentSameKeyCommitsOneBusinessChangeAndOneLogicalCall() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Map<String,Object>> call=()->{assertTrue(start.await(3,TimeUnit.SECONDS));return write(args(),lock->actor,ctx->{jdbc.update("INSERT INTO effects VALUES (?)",ctx.operationId());return Map.of("version",8);});};
            var a=pool.submit(call);var b=pool.submit(call);start.countDown();var first=a.get(10,TimeUnit.SECONDS);var second=b.get(10,TimeUnit.SECONDS);
            assertEquals(first.get("operationId"),second.get("operationId"));assertNotEquals(first.get("replayed"),second.get("replayed"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT used FROM meter",Integer.class));
        } finally{pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test void successfulReceiptSurvivesWriteRevocationButNotReadRevocation() {
        var read=new AtomicBoolean(true);var write=new AtomicBoolean(true);
        AgentProtocolStore.Authorization auth=lock->{if(!read.get())throw new MSException("AGENT_RESOURCE_NOT_ACCESSIBLE");return actor;};
        var first=write(args(),auth,ctx->{if(!write.get())throw new MSException("PROTOCOL_WRITE_DENIED");jdbc.update("INSERT INTO effects VALUES (?)",ctx.operationId());return Map.of("version",8);});
        write.set(false);var replay=write(new LinkedHashMap<>(args()),auth,ctx->{throw new AssertionError("must not reexecute");});assertEquals(first.get("operationId"),replay.get("operationId"));assertEquals(true,replay.get("replayed"));
        var other=new LinkedHashMap<>(args());other.put("idempotencyKey","new");assertEquals("PROTOCOL_WRITE_DENIED",assertThrows(MSException.class,()->write(other,auth,ctx->{throw new MSException("PROTOCOL_WRITE_DENIED");})).getMessage());
        read.set(false);assertEquals("AGENT_RESOURCE_NOT_ACCESSIBLE",assertThrows(MSException.class,()->write(args(),auth,ctx->Map.of())).getMessage());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
    }
    @Test void conflictDoesNotMeterAndCanonicalObjectOrderingDoesNotConflict() {
        var first=write(args(),lock->actor,ctx->Map.of("version",8));var reordered=new LinkedHashMap<>(args());reordered.put("value",new LinkedHashMap<>(Map.of("a",1,"b",2)));assertEquals(first.get("operationId"),write(reordered,lock->actor,ctx->Map.of()).get("operationId"));
        var changed=new LinkedHashMap<>(args());changed.put("leaseGeneration",3);assertEquals("IDEMPOTENCY_CONFLICT",assertThrows(MSException.class,()->write(changed,lock->actor,ctx->Map.of())).getMessage());assertEquals(1,jdbc.queryForObject("SELECT used FROM meter",Integer.class));
    }
    @Test void rejectedRequestRetainsOneCallButRollsBackAllBusinessWritesAndCanBeRechecked() {
        assertThrows(MSException.class,()->write(args(),lock->actor,ctx->{jdbc.update("INSERT INTO effects VALUES ('must-rollback')");throw new MSException("VERSION_CONFLICT");}));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT used FROM meter",Integer.class));
        var result=write(args(),lock->actor,ctx->{jdbc.update("INSERT INTO effects VALUES ('committed')");return Map.of("version",8);});assertEquals(false,result.get("replayed"));assertEquals(1,jdbc.queryForObject("SELECT used FROM meter",Integer.class));
    }
    @Test void tokenIdentifierIsNotPartOfStableScopeButAgentAndResourceAre() {
        var first=write(args(),lock->actor,ctx->Map.of("version",8));assertEquals(first.get("operationId"),write(args(),lock->new AgentProtocolAccess.Actor("org","user","agent","person",false),ctx->Map.of()).get("operationId"));
        var second=write(args(),lock->new AgentProtocolAccess.Actor("org","user","different-agent","person",false),ctx->Map.of("version",1));assertNotEquals(first.get("operationId"),second.get("operationId"));
    }
    @Test void protectedTaskEvidenceCannotBeDeletedAndDeletedFirstCannotBeReferenced() {
        store.transaction(()->evidence.hold("project","case","task","instance",List.of(Map.of("kind","TASK","id","image")),"UNKNOWN"));assertFalse(evidence.claimTaskDeletion("image",2));
        jdbc.update("DELETE FROM msp_protocol_evidence_hold");assertTrue(evidence.claimTaskDeletion("image",2));assertEquals("PROTOCOL_DELETE_PENDING",jdbc.queryForObject("SELECT status FROM ai_execution_artifact WHERE id='image'",String.class));
        assertThrows(MSException.class,()->store.transaction(()->evidence.hold("project","case","task","new-instance",List.of(Map.of("kind","TASK","id","image")),"RESULT")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_evidence_hold",Integer.class));
    }
    @Test void protectionWinsConcurrentDeleteAtTheActualEvidenceRow() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var held=new CountDownLatch(1);var finish=new CountDownLatch(1);var deleteStarted=new CountDownLatch(1);
        try {
            var protect=pool.submit(()->store.transaction(()->{evidence.hold("project","case","task","instance",List.of(Map.of("kind","TASK","id","image")),"DISPUTE");held.countDown();try{assertTrue(finish.await(5,TimeUnit.SECONDS));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}return true;}));
            assertTrue(held.await(3,TimeUnit.SECONDS));var delete=pool.submit(()->{deleteStarted.countDown();return evidence.claimTaskDeletion("image",2);});assertTrue(deleteStarted.await(3,TimeUnit.SECONDS));finish.countDown();assertTrue(protect.get(5,TimeUnit.SECONDS));assertFalse(delete.get(5,TimeUnit.SECONDS));
            assertEquals("AVAILABLE",jdbc.queryForObject("SELECT status FROM ai_execution_artifact WHERE id='image'",String.class));
        } finally{finish.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test void personalCleanupHonorsGovernedHoldsAndExistingReferencesWithoutExtendingOtherEvidence() {
        store.transaction(()->evidence.hold("project","case",null,"instance",List.of(Map.of("kind","PERSONAL","id","personal")),"RESULT"));assertEquals(0,evidence.cleanupPersonal(2));
        jdbc.update("DELETE FROM msp_protocol_evidence_hold");jdbc.update("INSERT INTO agent_case_submission_evidence VALUES ('legacy','personal',10)");assertEquals(0,evidence.cleanupPersonal(2));assertEquals(1,evidence.cleanupPersonal(11));
        assertThrows(MSException.class,()->store.transaction(()->evidence.hold("project","case",null,"instance",List.of(Map.of("kind","PERSONAL","id","personal")),"RESULT")));
    }
    @Test void encryptedStateCannotBeReadAsPlaintextFromTheDatabase() {
        write(args(),lock->actor,ctx->Map.of("privateObservation","personal-data"));String stored=jdbc.queryForObject("SELECT response FROM msp_protocol_operation",String.class);assertFalse(stored.contains("personal-data"));assertTrue(AgentFactCipher.protectedValue(stored));
    }

    @Test void legacyCaseInsensitiveEvidenceIdsCannotCreateUnprotectedAliases(){if(mysql()){jdbc.execute("ALTER TABLE ai_execution_artifact CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");jdbc.execute("ALTER TABLE agent_case_evidence CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");}assertThrows(MSException.class,()->store.transaction(()->evidence.hold("project","case","task","instance",List.of(Map.of("kind","TASK","id","IMAGE")),"UNKNOWN")));store.transaction(()->evidence.hold("project","case","task","instance",List.of(Map.of("kind","TASK","id","image")),"UNKNOWN"));assertFalse(evidence.claimTaskDeletion("IMAGE",2));assertFalse(evidence.claimTaskDeletion("image",2));assertEquals("AVAILABLE",jdbc.queryForObject("SELECT status FROM ai_execution_artifact WHERE id='image'",String.class));assertThrows(MSException.class,()->store.transaction(()->evidence.hold("project","case",null,"instance",List.of(Map.of("kind","PERSONAL","id","PERSONAL")),"RESULT")));}
}
