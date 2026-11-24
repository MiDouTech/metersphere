package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class EvidenceCatalogServiceTests {
    @AfterEach void clear(){AgentTokenContext.clear();}
    @Test void publishedPersonalEvidenceIsDiscoverableButStagingAndRejectedSubmissionsAreNot() {
        JdbcTemplate db=database();
        db.update("INSERT INTO functional_case VALUES('case','project',0)");
        db.update("INSERT INTO agent_case_submission VALUES('accepted','project','case','ACCEPTED'),('rejected','project','case','REJECTED')");
        db.update("INSERT INTO agent_case_evidence VALUES('published','project','case','accepted','proof.png','image/png',3,'hash','owner',1,?)",new byte[]{1,2,3});
        db.update("INSERT INTO agent_case_evidence VALUES('staged','project','case',NULL,'draft.png','image/png',3,'hash','owner',1,?)",new byte[]{4,5,6});
        db.update("INSERT INTO agent_case_submission_evidence VALUES('accepted','published'),('accepted','published'),('rejected','staged')");
        var access=mock(AgentBusinessAccess.class);when(access.userHasPermission("owner","project","FUNCTIONAL_CASE:READ")).thenReturn(true);
        var service=new EvidenceCatalogService(db,access,mock(AgentProjectService.class),provider(mock(ExternalExecutorService.class)),provider(mock(io.metersphere.bug.service.BugAttachmentService.class)),legacyProvider());
        var token=new AgentToken();token.setId("token");token.setUserId("owner");AgentTokenContext.set(token);
        when(access.activeToken()).thenReturn(token);
        var rows=service.rows("project");
        assertEquals(1,rows.size());assertEquals("PERSONAL:published",rows.getFirst().getId());
        assertEquals("PERSONAL_SUBMISSION",rows.getFirst().getSourceKind());
        db.update("INSERT INTO test_asset_category VALUES('category','Governed evidence','Parent / Governed evidence',0)");
        db.update("INSERT INTO test_asset_metadata VALUES('project','EVIDENCE','PERSONAL:published','MANUAL','category','AUDIT','audit-record')");
        var page=service.search("project","proof","PUBLISHED",0L,List.of(),null,false,1,20);
        assertEquals(1,page.getTotal());assertEquals("PERSONAL:published",page.getList().getFirst().getId());
        var detail=service.detail("project","PERSONAL:published");
        assertEquals(page.getList().getFirst().getCategoryId(),detail.getCategoryId());
        assertEquals("Governed evidence",detail.getCategoryName());assertEquals("Parent / Governed evidence",detail.getCategoryPath());
        assertEquals("MANUAL",detail.getCreationSource());assertEquals("AUDIT",detail.getSourceReferenceType());
        assertEquals("PERSONAL_SUBMISSION",detail.getSourceKind());
        db.update("INSERT INTO test_asset_version VALUES('version','project','EVIDENCE','PERSONAL:published',1,'hash','hash','{}','PUBLISHED','owner',1,'owner',1)");
        var versions=service.versions("project","PERSONAL:published",null,List.of(),null,false,1,20);
        assertEquals(1,versions.getTotal());assertEquals("version",versions.getList().getFirst().getId());
        assertArrayEquals(new byte[]{1,2,3},service.content("project","PERSONAL:published").bytes());
        assertThrows(RuntimeException.class,()->service.content("other","PERSONAL:published"));
        when(access.userHasPermission("owner","project","FUNCTIONAL_CASE:READ")).thenReturn(false);
        assertThrows(RuntimeException.class,()->service.content("project","PERSONAL:published"));
    }
    @Test void executorRequiresRealReferenceAndTheCurrentTokenSession() {
        var db=database();
        if(!nativeJson()) db.execute("CREATE ALIAS JSON_EXTRACT FOR 'io.metersphere.agent.service.EvidenceCatalogServiceTests.jsonExtract'");
        if(!nativeJson()) db.execute("CREATE ALIAS JSON_QUOTE FOR 'io.metersphere.agent.service.EvidenceCatalogServiceTests.jsonQuote'");
        if(!nativeJson()) db.execute("CREATE ALIAS JSON_CONTAINS FOR 'io.metersphere.agent.service.EvidenceCatalogServiceTests.jsonContains'");
        db.execute("CREATE TABLE external_executor_session(id VARCHAR(255),project_id VARCHAR(255),actor_id VARCHAR(255),token_id VARCHAR(255))");
        db.execute("CREATE TABLE external_executor_evidence(id VARCHAR(255),session_id VARCHAR(255),content_type VARCHAR(255),create_time BIGINT,content_hash VARCHAR(255))");
        db.execute("CREATE TABLE external_executor_event(session_id VARCHAR(255),evidence_id VARCHAR(255))");
        db.execute("CREATE TABLE business_asset_version(project_id VARCHAR(255),asset_id VARCHAR(255),status VARCHAR(255),snapshot_json VARCHAR(255))");
        db.execute("CREATE TABLE business_asset_reader(asset_id VARCHAR(255),user_id VARCHAR(255))");
        db.update("INSERT INTO external_executor_session VALUES('session','project','owner','token'),('foreign','project','other','another-token')");
        db.update("INSERT INTO external_executor_evidence VALUES('proof','session','image/png',1,'hash'),('draft','session','image/png',2,'hash'),('foreign-proof','foreign','image/png',3,'hash')");
        db.update("INSERT INTO external_executor_event VALUES('session','proof'),('foreign','foreign-proof')");
        var access=mock(AgentBusinessAccess.class);when(access.userHasPermission("owner","project","BUSINESS_ASSET:READ")).thenReturn(true);
        var token=new AgentToken();token.setId("token");token.setUserId("owner");AgentTokenContext.set(token);when(access.activeToken()).thenReturn(token);
        var executor=mock(ExternalExecutorService.class);
        when(executor.evidence("project","proof")).thenReturn(Map.of("contentType","image/png","content",Base64.getEncoder().encodeToString(new byte[]{8,9})));
        var service=new EvidenceCatalogService(db,access,mock(AgentProjectService.class),provider(executor),provider(mock(io.metersphere.bug.service.BugAttachmentService.class)),legacyProvider());
        assertEquals(List.of("EXECUTOR:proof"),service.rows("project").stream().map(r->r.getId()).toList());
        assertArrayEquals(new byte[]{8,9},service.content("project","EXECUTOR:proof").bytes());
        assertThrows(RuntimeException.class,()->service.content("project","EXECUTOR:draft"));
        assertThrows(RuntimeException.class,()->service.content("project","EXECUTOR:foreign-proof"));
        db.update("DELETE FROM external_executor_event");
        db.update("INSERT INTO business_asset_version VALUES('project','asset','CURRENT',?)","{\"branches\":[{\"evidenceRefs\":[\"proof\"]}]}");
        db.update("INSERT INTO business_asset_version VALUES('project','asset','HISTORICAL',?)","{\"branches\":[{\"evidenceRefs\":[\"proof\"]}]}");
        db.update("INSERT INTO business_asset_version VALUES('project','conflict','CONFLICT',?)","{\"branches\":[{\"evidenceRefs\":[\"draft\"]}]}");
        assertEquals(1,service.rows("project").size());
        db.update("INSERT INTO business_asset_reader VALUES('asset','other')");assertTrue(service.rows("project").isEmpty());
    }
    public static byte[] png(){try{var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}catch(java.io.IOException failure){throw new RuntimeException(failure);}}
    public static boolean jsonValid(String value){try{return new com.fasterxml.jackson.databind.ObjectMapper().readTree(value)!=null;}catch(Exception invalid){return false;}}
    public static String jsonUnquote(String value)throws Exception{return new com.fasterxml.jackson.databind.ObjectMapper().readTree(value).asText();}
    public static String jsonExtract(String value,String path)throws Exception {return new com.fasterxml.jackson.databind.ObjectMapper().readTree(value).path("branches").toString();}
    public static String jsonQuote(String value)throws Exception{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);}
    public static boolean jsonContains(String value,String candidate)throws Exception{return contains(new com.fasterxml.jackson.databind.ObjectMapper().readTree(value),new com.fasterxml.jackson.databind.ObjectMapper().readTree(candidate));}
    private static boolean contains(com.fasterxml.jackson.databind.JsonNode node,com.fasterxml.jackson.databind.JsonNode value){if(node.equals(value))return true;for(var child:node)if(contains(child,value))return true;return false;}
    @Test void bugImagesAndHistoricalArtifactsKeepDistinctSourcesAndRejectUnlinkedOrOtherProjects() {
        var db=database();
        db.execute("CREATE TABLE bug(id VARCHAR(255),project_id VARCHAR(255),deleted INT)");
        db.execute("CREATE TABLE bug_local_attachment(id VARCHAR(255),bug_id VARCHAR(255),file_name VARCHAR(255),file_id VARCHAR(255),source VARCHAR(255),create_user VARCHAR(255),create_time BIGINT)");
        db.execute("CREATE TABLE IF NOT EXISTS ai_execution_task(id VARCHAR(255),project_id VARCHAR(255))");
        db.execute("CREATE TABLE IF NOT EXISTS ai_execution_artifact(id VARCHAR(255),task_id VARCHAR(255),file_name VARCHAR(255),purpose VARCHAR(255),status VARCHAR(255),create_user VARCHAR(255),create_time BIGINT,sha256 VARCHAR(255),content_type VARCHAR(255),redacted INT)");
        db.update("INSERT INTO bug VALUES('bug','project',0),('other-bug','other',0)");
        db.update("INSERT INTO bug_local_attachment VALUES('image','bug','proof.png','file','ATTACHMENT','owner',1),('document','bug','report.xlsx','doc','ATTACHMENT','owner',1),('other','other-bug','proof.png','file','ATTACHMENT','owner',1)");
        db.update("INSERT INTO ai_execution_task VALUES('old-task','project')");
        db.update("INSERT INTO ai_execution_artifact VALUES('image','old-task','old.png','EVIDENCE','AVAILABLE','owner',1,'hash','image/png',1),('pending','old-task','pending.png','EVIDENCE','PREPARED','owner',1,'hash','image/png',1)");
        var access=mock(AgentBusinessAccess.class);when(access.userHasPermission("owner","project","PROJECT_BUG:READ")).thenReturn(true);when(access.userHasPermission("owner","project","AI_EXECUTION:READ")).thenReturn(true);
        var token=new AgentToken();token.setId("token");token.setUserId("owner");AgentTokenContext.set(token);when(access.activeToken()).thenReturn(token);
        var attachments=mock(io.metersphere.bug.service.BugAttachmentService.class);
        byte[] png=png();
        when(attachments.downloadOrPreview(any())).thenAnswer(call->{
            io.metersphere.bug.dto.request.BugFileSourceRequest request=call.getArgument(0);
            assertEquals("file",request.getFileId());assertEquals("bug",request.getBugId());assertEquals("project",request.getProjectId());
            return org.springframework.http.ResponseEntity.ok(png);
        });
        var service=new EvidenceCatalogService(db,access,mock(AgentProjectService.class),provider(mock(ExternalExecutorService.class)),provider(attachments),legacyProvider());
        assertArrayEquals(png,service.content("project","BUG:image").bytes());
        assertEquals(Set.of("BUG:image","image"),new HashSet<>(service.rows("project").stream().map(r->r.getId()).toList()));
        assertThrows(RuntimeException.class,()->service.detail("project","BUG:document"));assertThrows(RuntimeException.class,()->service.detail("project","BUG:other"));
    }
    @Test void resolveContextUsesAuthorizedNewEvidenceAndRealImmutablePublishedVersions() throws Exception {
        var db=database();db.update("INSERT INTO functional_case VALUES('case','project',0)");
        db.update("INSERT INTO agent_case_submission VALUES('submission','project','case','ACCEPTED')");
        db.update("INSERT INTO agent_case_evidence VALUES('proof','project','case','submission','proof.png','image/png',3,'hash','owner',1,?)",new byte[]{1,2,3});
        db.update("INSERT INTO agent_case_evidence VALUES('staged','project','case',NULL,'staged.png','image/png',3,'hash','owner',1,?)",new byte[]{1,2,3});
        db.update("INSERT INTO agent_case_submission_evidence VALUES('submission','proof')");
        db.update("INSERT INTO test_asset_version VALUES('old','project','EVIDENCE','PERSONAL:proof',1,'hash','old-hash','immutable-old','PUBLISHED','owner',1,'owner',1)");
        db.update("INSERT INTO test_asset_version VALUES('new','project','EVIDENCE','PERSONAL:proof',2,'hash','new-hash','immutable-new','PUBLISHED','owner',2,'owner',2)");
        db.update("INSERT INTO test_asset_version VALUES('foreign','other','EVIDENCE','PERSONAL:proof',1,'hash','foreign-hash','foreign','PUBLISHED','owner',1,'owner',1)");
        if(!nativeJson()){
            db.execute("CREATE ALIAS JSON_EXTRACT FOR 'io.metersphere.agent.service.EvidenceCatalogServiceTests.jsonExtract'");
            db.execute("CREATE ALIAS JSON_QUOTE FOR 'io.metersphere.agent.service.EvidenceCatalogServiceTests.jsonQuote'");
            db.execute("CREATE ALIAS JSON_CONTAINS FOR 'io.metersphere.agent.service.EvidenceCatalogServiceTests.jsonContains'");
        }
        db.execute("CREATE TABLE external_executor_session(id VARCHAR(255),project_id VARCHAR(255),actor_id VARCHAR(255),token_id VARCHAR(255))");
        db.execute("CREATE TABLE external_executor_evidence(id VARCHAR(255),session_id VARCHAR(255),content_type VARCHAR(255),create_time BIGINT,content_hash VARCHAR(255))");
        db.execute("CREATE TABLE external_executor_event(session_id VARCHAR(255),evidence_id VARCHAR(255))");
        db.execute("CREATE TABLE business_asset_version(project_id VARCHAR(255),asset_id VARCHAR(255),status VARCHAR(255),snapshot_json VARCHAR(255))");
        db.execute("CREATE TABLE business_asset_reader(asset_id VARCHAR(255),user_id VARCHAR(255))");
        db.execute("CREATE TABLE bug(id VARCHAR(255),project_id VARCHAR(255),deleted INT)");
        db.execute("CREATE TABLE bug_local_attachment(id VARCHAR(255),bug_id VARCHAR(255),file_name VARCHAR(255),file_id VARCHAR(255),source VARCHAR(255),create_user VARCHAR(255),create_time BIGINT)");
        db.update("INSERT INTO external_executor_session VALUES('session','project','owner','token')");
        db.update("INSERT INTO external_executor_evidence VALUES('exec-proof','session','image/png',1,'hash')");
        db.update("INSERT INTO external_executor_event VALUES('session','exec-proof')");
        db.update("INSERT INTO bug VALUES('bug','project',0)");db.update("INSERT INTO bug_local_attachment VALUES('bug-proof','bug','bug.png','file','ATTACHMENT','owner',1)");
        db.update("INSERT INTO test_asset_version VALUES('exec-version','project','EVIDENCE','EXECUTOR:exec-proof',1,'hash','hash','executor-fixed','PUBLISHED','owner',1,'owner',1)");
        db.update("INSERT INTO test_asset_version VALUES('bug-version','project','EVIDENCE','BUG:bug-proof',1,'hash','hash','bug-fixed','PUBLISHED','owner',1,'owner',1)");
        var access=mock(AgentBusinessAccess.class);when(access.userHasPermission("owner","project","FUNCTIONAL_CASE:READ")).thenReturn(true);
        when(access.userHasPermission("owner","project","BUSINESS_ASSET:READ")).thenReturn(true);
        when(access.userHasPermission("owner","project","PROJECT_BUG:READ")).thenReturn(true);
        var token=new AgentToken();token.setId("token");token.setUserId("owner");AgentTokenContext.set(token);when(access.activeToken()).thenReturn(token);
        var projects=mock(AgentProjectService.class);when(projects.resolveProjectId("project")).thenReturn("project");
        var projection=new EvidenceCatalogService(db,access,projects,provider(mock(ExternalExecutorService.class)),provider(mock(io.metersphere.bug.service.BugAttachmentService.class)),legacyProvider());
        var factory=new org.mybatis.spring.SqlSessionFactoryBean();factory.setDataSource(db.getDataSource());
        var xml=new org.springframework.core.io.ClassPathResource("io/metersphere/agent/mapper/TestAssetMapper.xml");
        if(nativeJson())factory.setMapperLocations(xml);
        else try(var input=xml.getInputStream()){
            // H2 lacks MySQL bit-literal syntax; equivalent boolean constant only. MySQL uses original mapper.
            String compatible=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("b'0'","0");
            factory.setMapperLocations(new org.springframework.core.io.ByteArrayResource(compatible.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        var subject=mock(org.apache.shiro.subject.Subject.class);when(subject.isPermitted(anyString())).thenReturn(true);org.apache.shiro.util.ThreadContext.bind(subject);
        try(var session=factory.getObject().openSession(true)){
            var mapper=session.getMapper(io.metersphere.agent.mapper.TestAssetMapper.class);
            var versions=new TestAssetVersionService();org.springframework.test.util.ReflectionTestUtils.setField(versions,"mapper",mapper);
            var catalog=new TestAssetCatalogService();org.springframework.test.util.ReflectionTestUtils.setField(catalog,"mapper",mapper);org.springframework.test.util.ReflectionTestUtils.setField(catalog,"agentProjectService",projects);org.springframework.test.util.ReflectionTestUtils.setField(catalog,"versionService",versions);org.springframework.test.util.ReflectionTestUtils.setField(catalog,"evidenceCatalog",projection);
            var ref=new io.metersphere.agent.dto.TestAssetRefDTO();ref.setAssetType("EVIDENCE");ref.setAssetId("PERSONAL:proof");ref.setVersionId("old");
            assertEquals("immutable-old",catalog.resolveContext("project",List.of(ref)).getFirst().getContentSnapshot());
            ref.setVersionId("foreign");assertThrows(RuntimeException.class,()->catalog.resolveContext("project",List.of(ref)));
            ref.setVersionId("missing");assertThrows(RuntimeException.class,()->catalog.resolveContext("project",List.of(ref)));
            ref.setVersionId(null);assertEquals("immutable-new",catalog.resolveContext("project",List.of(ref)).getFirst().getContentSnapshot());
            ref.setVersionId("old");assertEquals("immutable-old",catalog.resolveContext("project",List.of(ref)).getFirst().getContentSnapshot());
            if(!nativeJson()){
                db.execute("CREATE ALIAS JSON_VALID FOR 'io.metersphere.agent.service.EvidenceCatalogServiceTests.jsonValid'");
                db.execute("CREATE ALIAS JSON_UNQUOTE FOR 'io.metersphere.agent.service.EvidenceCatalogServiceTests.jsonUnquote'");
            }
            db.execute("ALTER TABLE functional_case ADD ref_id VARCHAR(255)");db.execute("ALTER TABLE functional_case ADD name VARCHAR(255)");db.execute("ALTER TABLE functional_case ADD latest INT DEFAULT 1");
            db.execute("ALTER TABLE ai_execution_task ADD name VARCHAR(255)");
            db.execute("CREATE TABLE ai_source_document(id VARCHAR(255),original_name VARCHAR(255))");
            db.execute("CREATE TABLE ai_execution_step(id VARCHAR(255),pos INT,instruction VARCHAR(255))");
            db.execute("CREATE TABLE test_asset_relation(id VARCHAR(255),project_id VARCHAR(255),relation_type VARCHAR(32),source_asset_type VARCHAR(32),source_asset_id VARCHAR(128),source_version_id VARCHAR(64),target_asset_type VARCHAR(32),target_asset_id VARCHAR(128),target_version_id VARCHAR(64),metadata TEXT,created_by VARCHAR(64),created_at BIGINT)");
            db.update("INSERT INTO test_asset_relation VALUES('readable','project','SUPPORTS','CASE','case',NULL,'EVIDENCE','PERSONAL:proof','old','{}','owner',1),('hidden-executor','project','SUPPORTS','CASE','case',NULL,'EVIDENCE','EXECUTOR:exec-proof','exec-version','{}','owner',4),('hidden-legacy','project','SUPPORTS','EVIDENCE','legacy-proof',NULL,'CASE','case',NULL,'{}','owner',3),('hidden-bug','project','SUPPORTS','EVIDENCE','BUG:bug-proof','bug-version','CASE','case',NULL,'{}','owner',2)");
            db.update("INSERT INTO ai_execution_task VALUES('legacy-task','project','Legacy task')");
            db.update("INSERT INTO ai_execution_artifact VALUES('legacy-proof','legacy-task','legacy.png','EVIDENCE','AVAILABLE','owner',1,'hash','image/png',1)");
            db.update("INSERT INTO test_asset_relation VALUES('hidden-executor-evidence','project','SUPPORTS','EVIDENCE','PERSONAL:proof','old','EVIDENCE','EXECUTOR:exec-proof','exec-version','{}','owner',5),('hidden-legacy-evidence','project','SUPPORTS','EVIDENCE','legacy-proof',NULL,'EVIDENCE','PERSONAL:proof','old','{}','owner',6)");
            when(access.userHasPermission("owner","project","BUSINESS_ASSET:READ")).thenReturn(false);when(access.userHasPermission("owner","project","PROJECT_BUG:READ")).thenReturn(false);
            var edges=catalog.relations("project",null,null,null,null,1,1);
            assertEquals(1,edges.getTotal());assertEquals("readable",edges.getList().getFirst().getId());
            assertEquals(0,catalog.relations("project","EVIDENCE","EXECUTOR:exec-proof",null,null,1,1).getTotal());
            assertEquals(0,catalog.relations("project","EVIDENCE","legacy-proof",null,null,1,1).getTotal());
            when(access.userHasPermission("owner","project","BUSINESS_ASSET:READ")).thenReturn(true);
            db.update("UPDATE external_executor_session SET token_id='different-token'");
            assertEquals(0,catalog.relations("project","EVIDENCE","EXECUTOR:exec-proof",null,null,1,1).getTotal());
            db.update("UPDATE external_executor_session SET token_id='token'");db.update("DELETE FROM external_executor_event");
            db.update("INSERT INTO business_asset_version VALUES('project','restricted','CURRENT',?)","{\"branches\":[{\"evidenceRefs\":[\"exec-proof\"]}]}");
            db.update("INSERT INTO business_asset_reader VALUES('restricted','other-user')");
            assertEquals(0,catalog.relations("project","EVIDENCE","EXECUTOR:exec-proof",null,null,1,1).getTotal());
            db.update("INSERT INTO external_executor_event VALUES('session','exec-proof')");
            when(access.userHasPermission("owner","project","PROJECT_BUG:READ")).thenReturn(true);
            for(var source:List.of(new String[]{"EXECUTOR:exec-proof","exec-version","executor-fixed","BUSINESS_ASSET:READ"},new String[]{"BUG:bug-proof","bug-version","bug-fixed","PROJECT_BUG:READ"})){
                ref.setAssetId(source[0]);ref.setVersionId(source[1]);assertEquals(source[2],catalog.resolveContext("project",List.of(ref)).getFirst().getContentSnapshot());
                ref.setVersionId("old");assertThrows(RuntimeException.class,()->catalog.resolveContext("project",List.of(ref)));
                ref.setVersionId(source[1]);when(access.userHasPermission("owner","project",source[3])).thenReturn(false);
                assertThrows(RuntimeException.class,()->catalog.resolveContext("project",List.of(ref)));when(access.userHasPermission("owner","project",source[3])).thenReturn(true);
            }
            ref.setAssetId("PERSONAL:staged");assertThrows(RuntimeException.class,()->catalog.resolveContext("project",List.of(ref)));
            ref.setAssetId("PERSONAL:proof");when(access.userHasPermission("owner","project","FUNCTIONAL_CASE:READ")).thenReturn(false);
            assertThrows(RuntimeException.class,()->catalog.resolveContext("project",List.of(ref)));
        } finally {org.apache.shiro.util.ThreadContext.unbindSubject();}
    }
    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> provider(T value){
        var provider=(org.springframework.beans.factory.ObjectProvider<T>)mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getObject()).thenReturn(value);return provider;
    }
    @SuppressWarnings("unchecked")
    private static org.springframework.beans.factory.ObjectProvider<AgentExecutionArtifactService> legacyProvider(){
        var provider=(org.springframework.beans.factory.ObjectProvider<AgentExecutionArtifactService>)mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getObject()).thenReturn(mock(AgentExecutionArtifactService.class));return provider;
    }
    @Test void lazyLegacyDependencyDoesNotCreateACatalogExecutionBeanCycle(){
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()){
            context.getBeanFactory().registerSingleton("jdbcTemplate",mock(JdbcTemplate.class));
            context.getBeanFactory().registerSingleton("access",mock(AgentBusinessAccess.class));
            context.getBeanFactory().registerSingleton("projects",mock(AgentProjectService.class));
            context.getBeanFactory().registerSingleton("executors",mock(ExternalExecutorService.class));
            context.getBeanFactory().registerSingleton("bugs",mock(io.metersphere.bug.service.BugAttachmentService.class));
            context.registerBean(AgentExecutionArtifactService.class,()->{context.getBean(EvidenceCatalogService.class);return mock(AgentExecutionArtifactService.class);}, definition->definition.setLazyInit(true));
            context.registerBean(EvidenceCatalogService.class);context.refresh();
            assertNotNull(context.getBean(EvidenceCatalogService.class));
        }
    }
    protected boolean nativeJson(){return false;}
    protected JdbcTemplate database(){
        var db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        initialize(db);return db;
    }
    protected void initialize(JdbcTemplate db){
        db.execute("CREATE TABLE test_asset_metadata(project_id VARCHAR(64),asset_type VARCHAR(32),asset_id VARCHAR(128),creation_source VARCHAR(32),category_id VARCHAR(64),source_reference_type VARCHAR(64),source_reference_id VARCHAR(128))");
        db.execute("CREATE TABLE test_asset_category(id VARCHAR(64),name VARCHAR(128),path VARCHAR(1024),deleted INT)");
        db.execute("CREATE TABLE test_asset_version(id VARCHAR(64),project_id VARCHAR(64),asset_type VARCHAR(32),asset_id VARCHAR(128),version_no INT,source_version VARCHAR(64),content_hash VARCHAR(64),content_snapshot TEXT,status VARCHAR(32),created_by VARCHAR(64),created_at BIGINT,published_by VARCHAR(64),published_at BIGINT)");
        db.execute("CREATE TABLE ai_execution_task(id VARCHAR(255),project_id VARCHAR(255))");
        db.execute("CREATE TABLE ai_execution_artifact(id VARCHAR(255),task_id VARCHAR(255),file_name VARCHAR(255),purpose VARCHAR(255),status VARCHAR(255),create_user VARCHAR(255),create_time BIGINT,sha256 VARCHAR(255),content_type VARCHAR(255),redacted INT)");
        db.execute("CREATE TABLE functional_case(id VARCHAR(64),project_id VARCHAR(64),deleted INT)");
        db.execute("CREATE TABLE agent_case_submission(id VARCHAR(64),project_id VARCHAR(64),case_id VARCHAR(64),gate_status VARCHAR(32))");
        db.execute("CREATE TABLE agent_case_evidence(id VARCHAR(64),project_id VARCHAR(64),case_id VARCHAR(64),submission_id VARCHAR(64),file_name VARCHAR(255),content_type VARCHAR(64),size_bytes BIGINT,sha256 VARCHAR(64),user_id VARCHAR(64),create_time BIGINT,payload BLOB)");
        db.execute("CREATE TABLE agent_case_submission_evidence(submission_id VARCHAR(64),evidence_id VARCHAR(64))");
    }
}
