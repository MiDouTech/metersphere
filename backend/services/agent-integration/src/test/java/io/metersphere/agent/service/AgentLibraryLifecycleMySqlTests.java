package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class AgentLibraryLifecycleMySqlTests {
    private final String schema="library_lifecycle_"+UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate admin,jdbc;
    private TransactionTemplate tx;
    private AgentBusinessAccess access;
    @BeforeEach void setup() throws Exception {
        String url="jdbc:mysql://127.0.0.1:13326/", options="?useSSL=false&allowPublicKeyRetrieval=true";
        admin=new JdbcTemplate(new DriverManagerDataSource(url+options,"root","quality-test-only"));
        admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        var ds=new DriverManagerDataSource(url+schema+options,"root","quality-test-only");
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        jdbc.execute("CREATE TABLE agent_token(id VARCHAR(50) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE functional_case(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),deleted BOOLEAN,name VARCHAR(255) DEFAULT 'Case',update_time BIGINT DEFAULT 10,module_id VARCHAR(50) DEFAULT 'NONE',ref_id VARCHAR(50),version_id VARCHAR(50),delete_time BIGINT)");
        jdbc.execute("CREATE TABLE ai_execution_task(id VARCHAR(50) PRIMARY KEY,status VARCHAR(32))");
        jdbc.execute("CREATE TABLE ai_execution_case(task_id VARCHAR(50),case_id VARCHAR(50))");
        jdbc.execute("CREATE TABLE agent_temp_attachment(file_id VARCHAR(50))");
        jdbc.execute("CREATE TABLE project(id VARCHAR(50),deleted BOOLEAN,enable BOOLEAN)");
        jdbc.update("INSERT INTO project VALUES ('project',0,1)");
        jdbc.execute("CREATE TABLE agent_case_submission(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),case_id VARCHAR(50),content_hash VARCHAR(64),policy_hash VARCHAR(64),result VARCHAR(20),gate_status VARCHAR(20),create_time BIGINT,facts MEDIUMTEXT)");
        jdbc.update("INSERT INTO agent_token VALUES ('token')");
        try(var connection=ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_109__agent_case_search_snapshot.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_110__agent_attachment_upload_intent.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_111__agent_case_deletion_preview.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_113__agent_upload_storage_guard.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_114__agent_trigger_dispatch.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_115__execution_archive.sql"));
        }
        jdbc.execute("ALTER TABLE agent_attachment_upload_intent ADD COLUMN identity_version VARCHAR(32) NOT NULL DEFAULT 'legacy-ci-v1',MODIFY COLUMN request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL");
        var token=new AgentToken();token.setId("token");token.setUserId("user");AgentTokenContext.set(token);
        access=mock(AgentBusinessAccess.class);
        when(access.userHasPermission(anyString(),anyString(),anyString())).thenReturn(true);
    }
    @AfterEach void cleanup(){AgentTokenContext.clear();if(admin!=null && schema.matches("library_lifecycle_[a-f0-9]{32}"))admin.execute("DROP DATABASE "+schema);}

    @Test void persistedScheduleResumesAfterDeliveryFailureWithoutRepeatingHistory() {
        jdbc.execute("CREATE TABLE ai_task_trigger(id VARCHAR(50) PRIMARY KEY)");
        jdbc.update("INSERT INTO ai_task_trigger VALUES ('trigger')");
        var service=new AgentTaskTriggerService();
        var mapper=mock(io.metersphere.agent.mapper.AgentTaskTriggerMapper.class);
        ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);
        ReflectionTestUtils.setField(service,"triggerTransactions",tx.getTransactionManager());
        ReflectionTestUtils.setField(service,"mapper",mapper);
        var trigger=new io.metersphere.agent.dto.AgentTaskTriggerDTO();
        trigger.setId("trigger");trigger.setEnabled(false);
        when(mapper.selectById("trigger")).thenReturn(trigger);
        jdbc.update("INSERT INTO ai_trigger_dispatch(trigger_id,scheduled_at,trigger_json,next_attempt) VALUES ('trigger',1,?,0)",io.metersphere.sdk.util.JSON.toJSONString(trigger));
        doThrow(new IllegalStateException("temporary history storage failure")).doNothing().when(mapper).insertHistory(any());
        try(var ids=mockStatic(io.metersphere.system.uid.IDGenerator.class)) {
        ids.when(io.metersphere.system.uid.IDGenerator::nextStr).thenAnswer(call->UUID.randomUUID().toString());
        service.drainScheduledDispatches();
        assertEquals("PENDING",jdbc.queryForObject("SELECT state FROM ai_trigger_dispatch",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT attempts FROM ai_trigger_dispatch",Integer.class));
        jdbc.update("UPDATE ai_trigger_dispatch SET next_attempt=0");
        service.drainScheduledDispatches();service.drainScheduledDispatches();
        assertEquals("DONE",jdbc.queryForObject("SELECT state FROM ai_trigger_dispatch",String.class));
        verify(mapper,times(2)).insertHistory(any());
        }
    }

    @Test void snapshotKeepsFrozenContentAndFailsWhenScopeDeleted() {
        var service=new AgentCaseSearchSnapshotService();var search=mock(AgentFunctionalCaseSearchService.class);
        ReflectionTestUtils.setField(service,"jdbc",jdbc);ReflectionTestUtils.setField(service,"search",search);ReflectionTestUtils.setField(service,"access",access);
        var response=new AgentCaseSearchResponse();response.setProjectId("project");response.setTotal(23);
        for(int i=0;i<23;i++){var row=new AgentCaseDTO();row.setCaseId("c"+i);row.setName("before"+i);row.setUpdateTime(10L);response.getCases().add(row);jdbc.update("INSERT INTO functional_case(id,project_id,deleted) VALUES (?,'project',0)",row.getCaseId());}
        when(search.search(any())).thenReturn(response);
        var frozen=tx.execute(status->service.create(new AgentCaseSearchRequest()));
        assertEquals(23,frozen.total());assertEquals(10,frozen.cases().size());
        response.getCases().getFirst().setName("changed later");
        assertEquals("before0",service.page(frozen.snapshotId(),1,10).cases().getFirst().getName());
        assertEquals(3,service.page(frozen.snapshotId(),3,10).cases().size());
        jdbc.update("UPDATE functional_case SET deleted=1 WHERE id='c22'");
        assertThrows(MSException.class,()->service.page(frozen.snapshotId(),1,10));
        service.release(frozen.snapshotId());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM agent_case_search_snapshot_item",Integer.class));
    }

    @Test void uploadCleanupResponsibilitySurvivesMetadataRollback() throws Exception {
        var guard=new AgentUploadStorageGuard();var storage=mock(io.metersphere.system.service.CommonFileService.class);
        ReflectionTestUtils.setField(guard,"jdbc",jdbc);ReflectionTestUtils.setField(guard,"files",storage);
        ReflectionTestUtils.setField(guard,"transactions",tx.getTransactionManager());
        try(var ids=mockStatic(io.metersphere.system.uid.IDGenerator.class)) {
            ids.when(io.metersphere.system.uid.IDGenerator::nextStr).thenReturn("storage");
            tx.executeWithoutResult(status->{guard.reserve("file.txt");guard.published("storage");status.setRollbackOnly();});
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agent_upload_storage_guard",Integer.class));
        jdbc.update("UPDATE agent_upload_storage_guard SET create_time=1");
        guard.cleanup();verify(storage).deleteExpiredTempUpload("storage","file.txt");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM agent_upload_storage_guard",Integer.class));
        jdbc.update("INSERT INTO agent_upload_storage_guard VALUES ('linked','file.txt',1)");jdbc.update("INSERT INTO agent_temp_attachment VALUES ('linked')");
        guard.cleanup();verify(storage,never()).deleteExpiredTempUpload(eq("linked"),anyString());
    }

    @Test void archiveRetriesIndependentlyAndAddsRedactedDataWithoutDuplicatingDocument() {
        assertEquals("[\"[REDACTED]\",{\"items\":[\"[REDACTED]\"]}]",AgentExecutionArchiveService.sanitize("[\"msat_private\",{\"items\":[\"msrt_private\"]}]"));
        jdbc.update("INSERT INTO agent_case_submission VALUES ('submission','project','case','hash','policy','SUCCESS','ACCEPTED',1,?)","{\"actual\":\"recorded observation\",\"password\":\"source-private-value\"}");
        var service=new AgentExecutionArchiveService();
        var files=mock(io.metersphere.project.service.FileMetadataService.class);
        var documents=mock(io.metersphere.functional.mapper.AiSourceDocumentMapper.class);
        ReflectionTestUtils.setField(service,"jdbc",jdbc);ReflectionTestUtils.setField(service,"access",access);
        ReflectionTestUtils.setField(service,"transactions",tx.getTransactionManager());ReflectionTestUtils.setField(service,"files",files);ReflectionTestUtils.setField(service,"documents",documents);
        ReflectionTestUtils.setField(service,"versions",mock(TestAssetVersionService.class));ReflectionTestUtils.setField(service,"governance",mock(TestAssetGovernanceService.class));
        var prepared=service.prepare("SUBMISSION","submission","user");String id=(String)prepared.get("id");
        String frozen=jdbc.queryForObject("SELECT payload FROM agent_execution_archive WHERE id=?",String.class,id);
        assertTrue(frozen.contains("recorded observation"));assertFalse(frozen.contains("source-private-value"));
        assertEquals(id,service.prepare("SUBMISSION","submission","user").get("id"));
        when(files.transferFileWithId(anyString(),anyString(),anyString(),eq("project"),anyString(),eq("user"),any())).thenThrow(new IllegalStateException("storage unavailable"));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->service.publish(id)));
        assertEquals("PENDING",service.status(id,"user").get("state"));
        assertEquals("SUCCESS",jdbc.queryForObject("SELECT result FROM agent_case_submission",String.class));
        when(files.transferFileWithId(anyString(),anyString(),anyString(),eq("project"),anyString(),eq("user"),any())).thenAnswer(call->call.getArgument(0));
        tx.executeWithoutResult(status->service.publish(id));assertEquals("DONE",service.status(id,"user").get("state"));
        var records=List.<Map<String,Object>>of(Map.of("sourceObjectId","order-1","reuseConditions","test project only","cleanupStatus","delete after reuse","values",Map.of("orderId","order-1","password","do-not-retain")));
        service.data(id,"user",records);service.data(id,"user",records);
        assertFalse(jdbc.queryForObject("SELECT data_payload FROM agent_execution_archive WHERE id=?",String.class,id).contains("do-not-retain"));
        tx.executeWithoutResult(status->service.publish(id));tx.executeWithoutResult(status->service.publish(id));
        assertNotNull(service.status(id,"user").get("datasetId"));verify(documents,times(1)).insert(any());
    }

    @Test void preparedUploadVerifiesHashAndReplaysOnlyOneStorageWrite() throws Exception {
        var service=new AgentAttachmentUploadIntentService();var storage=mock(AgentTempAttachmentService.class);
        ReflectionTestUtils.setField(service,"jdbc",jdbc);ReflectionTestUtils.setField(service,"access",access);ReflectionTestUtils.setField(service,"attachments",storage);
        byte[] bytes="hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request=new AgentAttachmentUploadIntentService.Prepare("project","CASE_DETAIL","sample.txt","text/plain",bytes.length,org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes),"upload-1");
        var prepared=tx.execute(status->service.prepare(request));
        assertEquals(prepared,tx.execute(status->service.prepare(request)));
        var response=new AgentTempAttachmentUploadResponse();response.setAttachmentId("temp-1");
        when(storage.upload(any(),eq("project"),eq("CASE_DETAIL"),isNull())).thenReturn(response);
        var bad=new MockMultipartFile("file","sample.txt","text/plain","wrong".getBytes());
        assertThrows(MSException.class,()->service.complete(prepared.uploadId(),bad));verifyNoInteractions(storage);
        var file=new MockMultipartFile("file","sample.txt","text/plain",bytes);
        assertEquals("temp-1",service.complete(prepared.uploadId(),file).getAttachmentId());
        assertEquals("temp-1",service.complete(prepared.uploadId(),file).getAttachmentId());
        verify(storage,times(1)).upload(any(),eq("project"),eq("CASE_DETAIL"),isNull());
        AgentTokenContext.get().setId("other");
        assertThrows(MSException.class,()->service.complete(prepared.uploadId(),file));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"CURRENT,c","ALL,","ALL,''"})
    void deletionRequiresWebDecisionThenReplaysAndRestoresCurrentVersion(String scope,String family) {
        jdbc.update("INSERT INTO functional_case(id,project_id,deleted,ref_id) VALUES ('c','project',0,?)",family);
        jdbc.update("INSERT INTO functional_case(id,project_id,deleted,ref_id) VALUES ('unrelated','project',0,?)",family);
        var service=new AgentCaseDeletionService();
        var cases=mock(io.metersphere.functional.service.FunctionalCaseService.class);
        var trash=mock(io.metersphere.functional.service.FunctionalCaseTrashService.class);
        ReflectionTestUtils.setField(service,"jdbc",jdbc);ReflectionTestUtils.setField(service,"access",access);
        ReflectionTestUtils.setField(service,"cases",cases);ReflectionTestUtils.setField(service,"trash",trash);
        ReflectionTestUtils.setField(service,"executions",mock(AgentExecutionService.class));
        doAnswer(call->{jdbc.update("UPDATE functional_case SET deleted=1 WHERE id='c'");return null;}).when(cases).handDeleteFunctionalCase(List.of("c"),false,"user","project");
        doAnswer(call->{jdbc.update("UPDATE functional_case SET deleted=0 WHERE id='c'");return null;}).when(trash).recoverVersions(List.of("c"),"user");
        var preview=tx.execute(status->service.preview("project",List.of("c"),scope));
        assertEquals(List.of("c"),preview.targets().stream().map(AgentCaseDeletionService.Target::id).toList());
        assertInstanceOf(AgentCaseDeletionService.Preview.class,service.execute(preview.previewId()));verifyNoInteractions(cases);
        tx.execute(status->service.decide(preview.previewId(),"user","DELETE"));
        var result=tx.execute(status->service.execute(preview.previewId()));
        assertEquals("COMPLETED",((Map<?,?>)result).get("state"));
        assertEquals(result,service.execute(preview.previewId()));
        verify(cases,times(1)).handDeleteFunctionalCase(List.of("c"),false,"user","project");
        assertEquals(1,((List<?>)service.restore("project","c",10,null,scope).get("items")).size());
        assertFalse(jdbc.queryForObject("SELECT deleted FROM functional_case WHERE id='c'",Boolean.class));
    }
}
