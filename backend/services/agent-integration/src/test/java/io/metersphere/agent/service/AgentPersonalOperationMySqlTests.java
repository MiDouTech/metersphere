package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** Real concurrent transactions in a newly created disposable schema, never application tables. */
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class AgentPersonalOperationMySqlTests {
    @Test void concurrentReplayCommitsOnceAndReauthorizes() throws Exception {
        String schema="quality_personal_"+UUID.randomUUID().toString().replace("-","");
        assertTrue(schema.matches("quality_personal_[a-f0-9]{32}"));
        String base="jdbc:mysql://127.0.0.1:13326/";
        String options="?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=10000";
        var admin=new JdbcTemplate(new DriverManagerDataSource(base+"quality_verify"+options,"root","quality-test-only"));
        admin.execute("CREATE DATABASE "+schema);
        var workers=Executors.newFixedThreadPool(2);
        try {
            var ds=new DriverManagerDataSource(base+schema+options,"root","quality-test-only");
            var jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE agent_idempotency_record(id VARCHAR(64) PRIMARY KEY,token_id VARCHAR(64),tool_name VARCHAR(128),request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,identity_version VARCHAR(32) NOT NULL DEFAULT 'legacy-ci-v1',request_hash CHAR(64),response_json MEDIUMTEXT,create_time BIGINT,status VARCHAR(32),hash_version VARCHAR(32),completed_time BIGINT,UNIQUE KEY(token_id,tool_name,request_id)) ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE writes(id VARCHAR(64) PRIMARY KEY) ENGINE=InnoDB");
            var operation=new AgentOperationExecutor(jdbc,new DataSourceTransactionManager(ds));
            var allowed=new AtomicBoolean(true);
            Runnable authorize=()->{if(!allowed.get())throw new MSException("AGENT_PERMISSION_DENIED");};
            var start=new CountDownLatch(1);
            Callable<Map> call=()->{
                setToken();
                try {
                    assertTrue(start.await(3,TimeUnit.SECONDS));
                    return operation.execute("test_plan.create","same-key",Map.of("name","plan"),Map.class,authorize,()->{
                        String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO writes VALUES (?)",id);
                        return Map.of("id",id);
                    });
                } finally {AgentTokenContext.clear();}
            };
            var first=workers.submit(call);var second=workers.submit(call);start.countDown();
            assertEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM writes",Integer.class));
            setToken();
            assertEquals("IDEMPOTENCY_CONFLICT",assertThrows(MSException.class,()->operation.execute("test_plan.create","same-key",Map.of("name","changed"),Map.class,authorize,()->Map.of())).getMessage());
            allowed.set(false);
            assertEquals("AGENT_PERMISSION_DENIED",assertThrows(MSException.class,()->operation.completed("test_plan.create","same-key",Map.of("name","plan"),Map.class,authorize)).getMessage());
            allowed.set(true);
            jdbc.update("INSERT INTO agent_idempotency_record(id,token_id,tool_name,request_id,request_hash,status,hash_version) VALUES ('old','t','metersphere.functional.execution.evidence.import_url','legacy-key','old','UNKNOWN','legacy-v0')");
            assertEquals("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED",assertThrows(MSException.class,()->operation.completed("external.image.execution","legacy-key",Map.of(),Map.class,authorize)).getMessage());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM writes",Integer.class));
            var upper=operation.execute("test_plan.create","CaseKey",Map.of("name","new"),Map.class,authorize,()->Map.of("id","UPPER"));
            var lower=operation.execute("test_plan.create","casekey",Map.of("name","new"),Map.class,authorize,()->Map.of("id","LOWER"));
            assertNotEquals(upper,lower);
            jdbc.update("INSERT INTO agent_idempotency_record(id,token_id,tool_name,request_id,request_hash,response_json,status,hash_version) VALUES ('folded','t','test_plan.create','LegacyKey',?,'{\"id\":\"original\"}','SUCCEEDED','canonical-v1')",operation.hash(Map.of("name","legacy")));
            assertEquals(Map.of("id","original"),operation.execute("test_plan.create","legacykey",Map.of("name","legacy"),Map.class,authorize,()->{throw new AssertionError("Legacy identity must not execute again");}));
            jdbc.update("INSERT INTO agent_idempotency_record(id,token_id,tool_name,request_id,request_hash,status,hash_version) VALUES ('unknown','t','test_plan.create','unknown',?,'UNKNOWN','canonical-v1')",operation.hash(Map.of("name","unknown")));
            assertEquals("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED",assertThrows(MSException.class,()->operation.completed("test_plan.create","unknown",Map.of("name","unknown"),Map.class,authorize)).getMessage());
            var configuration=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("test",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),ds));
            configuration.addMapper(io.metersphere.system.mapper.AgentIdempotencyRecordMapper.class);
            var factory=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration);
            try(var session=factory.openSession(true)) {
                var mapper=session.getMapper(io.metersphere.system.mapper.AgentIdempotencyRecordMapper.class);
                var record=new io.metersphere.system.domain.AgentIdempotencyRecord();record.setId("mapper");record.setTokenId("t");record.setToolName("generic");record.setRequestId("MapperKey");
                record.setRequestHash(AgentRequestFingerprint.hash(Map.of("nested",Map.of("a",1,"b",2))));record.setResponseJson("{\"ok\":true}");record.setCreateTime(0L);record.setHashVersion("generic-canonical-v1");
                mapper.insert(record);
                assertEquals("generic-canonical-v1",mapper.selectByTokenToolRequest("t","generic","MapperKey").getHashVersion());
                assertNull(mapper.selectByTokenToolRequest("t","generic","mapperkey"));
                var cache=new AgentIdempotencyService();org.springframework.test.util.ReflectionTestUtils.setField(cache,"agentIdempotencyRecordMapper",mapper);
                var reversed=new LinkedHashMap<String,Object>();reversed.put("b",2);reversed.put("a",1);
                assertEquals(Map.of("ok",true),cache.findCachedResponse("generic","MapperKey",Map.of("nested",reversed)).orElseThrow());
                assertEquals(1,mapper.deleteExpired(1L));
                assertNotNull(mapper.selectByTokenToolRequest("t","generic","MapperKey"));
                assertEquals("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED",assertThrows(MSException.class,()->cache.findCachedResponse("generic","MapperKey",Map.of("nested",reversed))).getMessage());
            }
        } finally {
            workers.shutdownNow();workers.awaitTermination(10,TimeUnit.SECONDS);AgentTokenContext.clear();
            admin.execute("DROP DATABASE "+schema);
        }
    }
    private static void setToken(){var token=new AgentToken();token.setId("t");token.setUserId("u");AgentTokenContext.set(token);}
}
