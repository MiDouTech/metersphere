package io.metersphere.agent.service;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentSyncIntentTests extends AgentProtocolTransactionTests {
    AgentProtocolPublicationService publisher;AgentProtocolToolService tools;AgentDirectExecutionService direct;
    Map<String,Object> request=Map.of("protocolVersion","1.0","idempotencyKey","same-sync-intent","sessionId","session","caseExecutionId","instance","expectedResultVersion",1);
    @BeforeEach void syncFixture(){
        jdbc.update("INSERT INTO msp_protocol_session(id,organization_id,project_id,owner_id,agent_ref,source_type,source_id,environment_id,state,version,context_version,payload,create_time) VALUES ('session','org','project','user','agent','PERSONAL','case','env','OPEN',1,1,?,1)",store.payload(Map.of()));
        jdbc.update("INSERT INTO msp_protocol_instance(id,session_id,case_id,phase,channel,verification,result,version,result_version,revision,generation,lease_owner,lease_until,calls_used,recoveries_used,recovery_ms,payload) VALUES ('instance','session','case','ENDED','CLOSED','ACCEPTED','PASS',1,1,1,1,'lease',1,0,0,0,?)",store.payload(Map.of()));
        var access=mock(AgentProtocolAccess.class);when(access.actor(eq("org"),anyBoolean())).thenReturn(new AgentProtocolAccess.Actor("org","reviewer","HUMAN","reviewer-person",true));direct=mock(AgentDirectExecutionService.class);publisher=new AgentProtocolPublicationService(jdbc,store,access,direct,mock(io.metersphere.agent.mapper.AgentExecutionMapper.class),mock(io.metersphere.agent.quality.GlobalQualityGate.class),mock(AgentExecutionCaseWritebackService.class));ReflectionTestUtils.setField(publisher,"lifecycle",new AgentProtocolTaskLifecycleService(jdbc,store));tools=new AgentProtocolToolService(mock(AgentGovernedExecutionService.class),mock(AgentKnowledgeService.class),publisher,mock(AgentProtocolImageService.class));
    }
    Map<?,?> sync(){return (Map<?,?>)tools.execute("result.sync",request);}
    Map<String,Object> formal(){jdbc.update("INSERT INTO effects VALUES ('formal')");jdbc.update("INSERT INTO msp_protocol_publication VALUES ('instance',1,'formal',1)");return Map.of("syncStatus","SYNCED");}
    @Test void failedSameKeyCanRecoverAndConcurrentRetriesCommitOnlyOneFormalEffect() throws Exception {
        var calls=new AtomicInteger();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(direct.publishGovernedPersonal(anyMap(),anyMap(),anyMap())).thenAnswer(call->{if(calls.incrementAndGet()==1)throw new io.metersphere.sdk.exception.MSException("INJECTED_TRANSIENT_FAILURE");entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return formal();});
        var failed=sync();assertEquals("FAILED",failed.get("syncStatus"));assertEquals("SYNC_PENDING",jdbc.queryForObject("SELECT status FROM msp_protocol_operation",String.class));
        var pool=Executors.newFixedThreadPool(2);try{var first=pool.submit(this::sync);assertTrue(entered.await(10,TimeUnit.SECONDS));var second=pool.submit(this::sync);release.countDown();var success=first.get(15,TimeUnit.SECONDS);var replay=second.get(15,TimeUnit.SECONDS);assertEquals("SYNCED",success.get("syncStatus"));assertEquals(true,replay.get("replayed"));assertEquals(failed.get("operationId"),success.get("operationId"));assertEquals(success.get("operationId"),replay.get("operationId"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));assertEquals(2,calls.get());}finally{release.countDown();pool.shutdownNow();}
    }
    @Test void failedReceiptSealedByThePreviousVersionCanStillRecover(){
        when(direct.publishGovernedPersonal(anyMap(),anyMap(),anyMap())).thenAnswer(call->formal());String scope=AgentProtocolData.hash(List.of("org","reviewer","HUMAN","result.sync","session","instance"));
        jdbc.update("INSERT INTO msp_protocol_operation VALUES (?,?,?,?,'SUCCEEDED',false,?,NULL,1)",scope,"same-sync-intent","original-operation",AgentProtocolData.requestHash(request),store.payload(Map.of("syncStatus","FAILED","operationId","original-operation")));
        var success=sync();assertEquals("SYNCED",success.get("syncStatus"));assertEquals("original-operation",success.get("operationId"));assertEquals(true,sync().get("replayed"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
    }
}
