package io.metersphere.agent.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the actual row fence shared by both formal adapters and the revocation transaction. */
@EnabledIfSystemProperty(named="protocol.mysql",matches="true")
class AgentKnowledgeSafetyMySqlRaceTests extends AgentProtocolTransactionTests {
    @Override protected boolean mysql(){return true;}
    @Test void aFormalWriterHoldingKnowledgeLocksCommitsBeforeRevocationAndItsResultIsThenMarkedForReview() throws Exception {
        jdbc.update("INSERT INTO msp_knowledge VALUES ('k','org','user','PUBLISHED',1,1,0,?,1)",store.payload(Map.of()));jdbc.update("INSERT INTO msp_knowledge_version(knowledge_id,version,state,payload,content_hash,author_id,create_time) VALUES ('k',1,'PUBLISHED',?,'hash','user',1)",store.payload(Map.of()));
        var session=Map.<String,Object>of("organization_id","org");var facts=Map.<String,Object>of("knowledge",List.of(Map.of("knowledgeId","k","version",1)));
        jdbc.update("INSERT INTO msp_protocol_session(id,organization_id,project_id,owner_id,agent_ref,source_type,source_id,environment_id,state,version,context_version,payload,create_time) VALUES ('s','org','project','user','agent','PERSONAL','case','env','OPEN',1,1,?,1)",store.payload(Map.of()));jdbc.update("INSERT INTO msp_protocol_instance(id,session_id,case_id,phase,channel,verification,result,version,result_version,revision,generation,lease_owner,lease_until,calls_used,recoveries_used,recovery_ms,payload) VALUES ('i','s','case','ENDED','CLOSED','ACCEPTED','PASS',1,1,1,1,'lease',1,0,0,0,?)",store.payload(facts));
        var locked=new CountDownLatch(1);var revocationStarted=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var writer=pool.submit(()->store.transaction(()->{AgentProtocolKnowledgeSafety.requireSafe(jdbc,session,facts);jdbc.update("INSERT INTO effects VALUES ('formal-pass')");locked.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return true;}));assertTrue(locked.await(10,TimeUnit.SECONDS));
            var revoke=pool.submit(()->store.transaction(()->{revocationStarted.countDown();jdbc.update("UPDATE msp_knowledge SET state='REVOKED',safety_generation=safety_generation+1 WHERE id='k'");jdbc.update("UPDATE msp_knowledge_version SET state='REVOKED' WHERE knowledge_id='k'");AgentProtocolKnowledgeSafety.revoke(jdbc,store,"org","k","revoke","human");return true;}));assertTrue(revocationStarted.await(10,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->revoke.get(150,TimeUnit.MILLISECONDS));release.countDown();assertTrue(writer.get(15,TimeUnit.SECONDS));assertTrue(revoke.get(15,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));var row=jdbc.queryForMap("SELECT * FROM msp_protocol_instance WHERE id='i'");assertEquals("PASS",row.get("result"));assertEquals("ACCEPTED",row.get("verification"));assertEquals(true,store.data(row).get("disputed"));assertThrows(io.metersphere.sdk.exception.MSException.class,()->store.transaction(()->{AgentProtocolKnowledgeSafety.requireSafe(jdbc,session,facts);return null;}));
        }finally{release.countDown();pool.shutdownNow();}
    }
}
