package io.metersphere.agent.service;

import io.metersphere.functional.excel.domain.FunctionalCaseExcelData;
import io.metersphere.functional.request.FunctionalCaseImportRequest;
import io.metersphere.functional.service.*;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.user.UserDTO;
import io.metersphere.system.service.UserLoginService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class AgentCaseImportMySqlTests {
    private final String schema="case_import_"+UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate admin,jdbc;
    private TransactionTemplate tx;
    private AgentCaseImportService service;
    private FunctionalCaseService cases;
    @BeforeEach void setup() throws Exception {
        String server="jdbc:mysql://127.0.0.1:13326/",options="?useSSL=false&allowPublicKeyRetrieval=true";
        admin=new JdbcTemplate(new DriverManagerDataSource(server+options,"root","quality-test-only"));admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        var source=new DriverManagerDataSource(server+schema+options,"root","quality-test-only");jdbc=new JdbcTemplate(source);
        var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);
        try(var connection=source.getConnection()) {
            for(String migration:List.of("112__functional_import_job", "123__functional_import_followup", "130__asset_import_followup_adapter", "135__import_commit_stage"))
                ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_"+migration+".sql"));
        }
        jdbc.execute("CREATE TABLE project(id VARCHAR(50),deleted BOOLEAN,enable BOOLEAN,organization_id VARCHAR(50))");jdbc.update("INSERT INTO project VALUES ('project',0,1,'org')");
        jdbc.execute("CREATE TABLE project_version(id VARCHAR(50),project_id VARCHAR(50))");jdbc.update("INSERT INTO project_version VALUES ('version','project')");
        jdbc.execute("CREATE TABLE import_business_write(name VARCHAR(100))");
        service=new AgentCaseImportService();cases=mock(FunctionalCaseService.class);
        var access=mock(AgentBusinessAccess.class);when(access.userHasPermission(anyString(),anyString(),anyString())).thenReturn(true);
        var files=mock(FunctionalCaseFileService.class);when(files.getCustomFields("project")).thenReturn(List.of());
        var modules=mock(FunctionalCaseModuleService.class);when(modules.getTree("project")).thenReturn(List.of());
        var users=mock(UserLoginService.class);var user=new UserDTO();user.setId("user");when(users.getUserDTO("user")).thenReturn(user);
        ReflectionTestUtils.setField(service,"jdbc",jdbc);ReflectionTestUtils.setField(service,"access",access);ReflectionTestUtils.setField(service,"transactionManager",manager);
        ReflectionTestUtils.setField(service,"files",files);ReflectionTestUtils.setField(service,"cases",cases);ReflectionTestUtils.setField(service,"modules",modules);ReflectionTestUtils.setField(service,"users",users);
        when(cases.getNextOrder("project")).thenReturn(1000L);
        doAnswer(call->{List<FunctionalCaseExcelData> rows=call.getArgument(0);jdbc.update("INSERT INTO import_business_write VALUES (?)",rows.getFirst().getName());return null;}).when(cases).saveImportData(anyList(),any(),anyList(),anyMap(),anyMap(),any(),any());
    }
    @AfterEach void cleanup(){if(admin!=null&&schema.matches("case_import_[a-f0-9]{32}"))admin.execute("DROP DATABASE "+schema);}
    private void job(String id,int count){
        var request=new FunctionalCaseImportRequest();request.setProjectId("project");request.setVersionId("version");long now=System.currentTimeMillis();
        jdbc.update("INSERT INTO functional_import_job(id,project_id,user_id,token_id,request_id,request_hash,request_json,template_hash,source_hash,source_name,source_bytes,create_time,update_time,expires_at) VALUES (?,'project','user','WEB',?,'hash',?,?,'source','cases.xlsx',?,?,?,?)",id,id,JSON.toJSONString(request),org.apache.commons.codec.digest.DigestUtils.sha256Hex("[]"),new byte[]{1},now,now,now+86400000);
        jdbc.update("INSERT INTO functional_import_followup(job_id,project_id,user_id,policy,request_json,group_id,state,create_time,update_time,source_kind) VALUES (?,'project','user','IMPORT_ONLY','{}','NONE','SKIPPED',?,?,'FILE')",id,now,now);
        for(int i=1;i<=count;i++){var item=new FunctionalCaseExcelData();item.setName("case"+i);jdbc.update("INSERT INTO functional_import_item(job_id,position,action,payload,state) VALUES (?,?,'CREATE',?,'PENDING')",id,i,JSON.toJSONString(item));}
    }
    @Test void stopResumeAndReplayNeverDuplicateCommittedItems(){
        job("job",2);service.action("job","commit","user","WEB");
        tx.execute(status->service.processOne("job"));
        service.action("job","stop","user","WEB");assertNull(tx.execute(status->service.processOne("job")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM import_business_write",Integer.class));
        service.action("job","retry","user","WEB");service.drain();service.drain();
        assertEquals("COMPLETED",service.get("job","user","WEB",1,10).get("state"));
        service.action("job","commit","user","WEB");service.drain();
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM import_business_write",Integer.class));
    }
    @Test void workerUsesFrozenTemplateLanguageAndRestoresThreadLanguage(){
        job("localized",1);
        jdbc.update("UPDATE functional_import_job SET request_json=JSON_SET(request_json,'$.importLocale','zh-CN') WHERE id='localized'");
        var previous=org.springframework.context.i18n.LocaleContextHolder.getLocaleContext();
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.ENGLISH);
        try {
            doAnswer(call->{
                assertEquals(java.util.Locale.SIMPLIFIED_CHINESE,org.springframework.context.i18n.LocaleContextHolder.getLocale());
                jdbc.update("INSERT INTO import_business_write VALUES ('localized')");return null;
            }).when(cases).saveImportData(anyList(),any(),anyList(),anyMap(),anyMap(),any(),any());
            service.action("localized","commit","user","WEB");
            tx.execute(status->service.processOne("localized"));
            assertEquals(java.util.Locale.ENGLISH,org.springframework.context.i18n.LocaleContextHolder.getLocale());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM import_business_write",Integer.class));
        } finally {org.springframework.context.i18n.LocaleContextHolder.setLocaleContext(previous);}
    }
    @Test void itemAndBusinessWriteRollBackTogetherAndRetryUsesFrozenData(){
        job("job",1);service.action("job","commit","user","WEB");
        tx.executeWithoutResult(status->{service.processOne("job");status.setRollbackOnly();});
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM import_business_write",Integer.class));
        assertEquals("PENDING",jdbc.queryForObject("SELECT state FROM functional_import_item",String.class));
        service.drain();assertEquals("case1",jdbc.queryForObject("SELECT name FROM import_business_write",String.class));
        assertEquals("COMPLETED",service.get("job","user","WEB",1,10).get("state"));
    }
}
