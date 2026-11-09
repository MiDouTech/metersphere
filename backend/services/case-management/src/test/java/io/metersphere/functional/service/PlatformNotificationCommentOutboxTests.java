package io.metersphere.functional.service;

import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.sdk.util.EncryptUtils;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.notice.*;
import io.metersphere.system.service.NoticeSendService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformNotificationCommentOutboxTests {
    JdbcTemplate jdbc; FunctionalCaseCommentOutbox outbox; NoticeSendService sender;
    FunctionalCaseCommentAccess access;
    @BeforeEach void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE functional_case(id VARCHAR(50) PRIMARY KEY,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE functional_case_comment(id VARCHAR(50) PRIMARY KEY,case_id VARCHAR(50),deleted BOOLEAN,create_user VARCHAR(50))");
        jdbc.update("INSERT INTO functional_case VALUES ('case',0)");
        jdbc.update("INSERT INTO functional_case_comment VALUES ('comment','case',0,'author')");
        try(var connection=source.getConnection()) { ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_108__case_comment_notice_outbox.sql")); }
        sender=mock(NoticeSendService.class);access=mock(FunctionalCaseCommentAccess.class);
        var owner=new FunctionalCase();owner.setId("case");owner.setProjectId("A");
        when(access.requireCase(anyString(),anyString(),anyBoolean())).thenReturn(owner);
        when(access.hasPermission(anyString(),anyString(),anyString())).thenReturn(true);
        outbox=new FunctionalCaseCommentOutbox();ReflectionTestUtils.setField(outbox,"jdbc",jdbc);
        ReflectionTestUtils.setField(outbox,"sender",sender);ReflectionTestUtils.setField(outbox,"access",access);
        ReflectionTestUtils.setField(outbox,"transactionManager",new DataSourceTransactionManager(source));
    }
    @AfterEach void close(){jdbc.execute("SHUTDOWN");}
    NoticeSendService.Delivery delivery(String source,String receiver,String channel) {
        var detail=new MessageDetail();detail.setProjectId(source);detail.setType(channel);detail.setEvent("AT");detail.setTaskType("FUNCTIONAL_CASE_TASK");detail.setSubject("frozen subject");
        var model=NoticeModel.builder().event("AT").operator("author").frozenRecipients(true).receivers(List.of(new Receiver(receiver,"MENTIONED_ME")))
                .paramMap(new HashMap<>(Map.of("projectId",source))).build();
        return new NoticeSendService.Delivery(detail,model);
    }
    String insert(String id,String state,String source,String receiver,String channel) {
        String payload=EncryptUtils.aesEncrypt(JSON.toJSONString(delivery(source,receiver,channel)));
        jdbc.update("INSERT INTO functional_case_comment_notice(id,comment_id,case_id,project_id,receiver_id,channel,payload,state,attempts,next_attempt,update_time) VALUES (?,'comment','case',?,?,?,?,?,3,0,10)",id,source,receiver,channel,payload,state);
        return payload;
    }
    @Test void oldFrozenPayloadStaysUnchangedAndNewEventsDeduplicateWithRealSource() {
        String oldPayload=insert("old","PENDING","old-project","old-member","IN_SITE");
        var fresh=delivery("A","alice","IN_SITE");when(sender.prepareDeliveries(eq("FUNCTIONAL_CASE_TASK"),any())).thenReturn(List.of(fresh));
        outbox.enqueue("comment",1,"case",fresh.model());outbox.enqueue("comment",1,"case",fresh.model());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_comment_notice",Integer.class));
        assertEquals(oldPayload,jdbc.queryForObject("SELECT payload FROM functional_case_comment_notice WHERE id='old'",String.class));
        var row=jdbc.queryForMap("SELECT * FROM functional_case_comment_notice WHERE id<>'old'");
        assertEquals("A",row.get("project_id"));assertEquals("alice",row.get("receiver_id"));
        var frozen=JSON.parseObject(EncryptUtils.aesDecrypt(row.get("payload")),NoticeSendService.Delivery.class);
        assertEquals("A",frozen.configuration().getProjectId());assertEquals("alice",frozen.model().getReceivers().getFirst().getUserId());
        clearInvocations(sender);outbox.dispatch("old");
        var captured=ArgumentCaptor.forClass(NoticeSendService.Delivery.class);verify(sender).deliver(captured.capture());
        assertEquals("old-project",captured.getValue().configuration().getProjectId());assertEquals("old-member",captured.getValue().model().getReceivers().getFirst().getUserId());
        verify(sender,never()).prepareDeliveries(anyString(),any());
        assertEquals("SENT",jdbc.queryForObject("SELECT state FROM functional_case_comment_notice WHERE id='old'",String.class));
        assertEquals(oldPayload,jdbc.queryForObject("SELECT payload FROM functional_case_comment_notice WHERE id='old'",String.class));
    }
    @Test void zeroRecipientsAndDisabledConfigurationProduceNoQueueEntries() {
        when(sender.prepareDeliveries(anyString(),any())).thenReturn(List.of());
        outbox.enqueue("comment",1,"case",delivery("A","alice","IN_SITE").model());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_comment_notice",Integer.class));verify(sender,never()).deliver(any());
    }
    @ParameterizedTest @ValueSource(strings={"REVOKED","COMMENT_DELETED","CASE_DELETED"})
    void deletedOrRevokedSourceCancelsWithoutDelivery(String change) {
        insert("old","PENDING","A","alice","IN_SITE");
        switch(change) {
            case "REVOKED" -> when(access.hasPermission(anyString(),anyString(),anyString())).thenReturn(false);
            case "COMMENT_DELETED" -> jdbc.update("UPDATE functional_case_comment SET deleted=1");
            case "CASE_DELETED" -> jdbc.update("UPDATE functional_case SET deleted=1");
        }
        outbox.dispatch("old");assertEquals("CANCELLED",jdbc.queryForObject("SELECT state FROM functional_case_comment_notice",String.class));verify(sender,never()).deliver(any());
    }
    @Test void staleSendingBecomesUncertainAndTerminalsAreNeverAutomaticallyReplayed() {
        for(String state:List.of("SENDING","SENT","SUBMITTED","UNCERTAIN")) insert(state,state,"A","alice","IN_SITE");
        outbox.drain();outbox.drain();verify(sender,never()).deliver(any());
        assertEquals("UNCERTAIN",jdbc.queryForObject("SELECT state FROM functional_case_comment_notice WHERE id='SENDING'",String.class));
        assertEquals(3,jdbc.queryForObject("SELECT attempts FROM functional_case_comment_notice WHERE id='SENDING'",Integer.class));
        for(String state:List.of("SENT","SUBMITTED","UNCERTAIN")) assertEquals(state,jdbc.queryForObject("SELECT state FROM functional_case_comment_notice WHERE id=?",String.class,state));
    }
    @ParameterizedTest @ValueSource(strings={"OK","AMBIGUOUS"})
    void externalAdapterReturnIsSubmittedAndAmbiguousFailureIsUncertain(String outcome) {
        insert("remote","PENDING","A","alice","WE_COM");
        if("AMBIGUOUS".equals(outcome)) doThrow(new IllegalStateException("test transport failure")).when(sender).deliver(any());
        outbox.dispatch("remote");assertEquals("OK".equals(outcome)?"SUBMITTED":"UNCERTAIN",jdbc.queryForObject("SELECT state FROM functional_case_comment_notice",String.class));
    }
    @ParameterizedTest @ValueSource(strings={"FAILED","UNCERTAIN","SUBMITTED"})
    void authorizedRetryPreservesPayloadAndResetsAttemptsWithVersionCheck(String state) {
        String payload=insert("retry",state,"A","alice","IN_SITE");
        var before=jdbc.queryForMap("SELECT * FROM functional_case_comment_notice");
        assertThrows(RuntimeException.class,()->outbox.retry("case","retry","outsider",10));assertEquals(before,jdbc.queryForMap("SELECT * FROM functional_case_comment_notice"));
        assertThrows(RuntimeException.class,()->outbox.retry("case","retry","author",9));assertEquals(before,jdbc.queryForMap("SELECT * FROM functional_case_comment_notice"));
        outbox.retry("case","retry","author",10);assertEquals("PENDING",jdbc.queryForObject("SELECT state FROM functional_case_comment_notice",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT attempts FROM functional_case_comment_notice",Integer.class));assertEquals(payload,jdbc.queryForObject("SELECT payload FROM functional_case_comment_notice",String.class));
    }
    @ParameterizedTest @ValueSource(strings={"PENDING","SENDING","SENT","CANCELLED"})
    void otherStatesCannotBeManuallyRetried(String state) {
        insert("retry",state,"A","alice","IN_SITE");var before=jdbc.queryForMap("SELECT * FROM functional_case_comment_notice");
        assertThrows(RuntimeException.class,()->outbox.retry("case","retry","author",10));assertEquals(before,jdbc.queryForMap("SELECT * FROM functional_case_comment_notice"));
    }
}
