package io.metersphere.project.service;

import io.metersphere.system.service.PlatformNotificationInitializer;
import io.metersphere.system.service.PlatformNotificationScope;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class PlatformNotificationInitializationTests {
    JdbcTemplate jdbc;
    PlatformNotificationInitializer initializer;

    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        jdbc = new JdbcTemplate(source);
        String ddl = new ClassPathResource("migration/3.0.0/ddl/V3.0.0_4__project_management.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        for (String table : List.of("project_robot", "message_task", "message_task_blob")) {
            var matcher = Pattern.compile("CREATE TABLE IF NOT EXISTS " + table + "\\s*\\([\\s\\S]*?;", Pattern.CASE_INSENSITIVE).matcher(ddl);
            assertTrue(matcher.find()); jdbc.execute(matcher.group());
        }
        jdbc.update("INSERT INTO project_robot(id,project_id,name,platform,webhook,enable,create_time,update_user,update_time) VALUES ('site',?,'robot_in_site','IN_SITE','NONE',1,1,'admin',1)", PlatformNotificationScope.ID);
        initializer = new PlatformNotificationInitializer(jdbc, new DataSourceTransactionManager(source));
    }
    @AfterEach void close() { jdbc.execute("SHUTDOWN"); }
    long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    List<Map<String,Object>> snapshot() { return jdbc.queryForList("SELECT * FROM message_task ORDER BY id"); }

    @Test void firstPreparationAndRestartHaveExactlySixLinkedDefaultsAndLeaveCustomAndProjectDataEmpty() {
        initializer.prepare(); var before = snapshot();
        initializer.run(null); initializer.prepare();
        assertEquals(before, snapshot()); assertEquals(6, count("message_task")); assertEquals(6, count("message_task_blob"));
        assertEquals(1, count("project_robot"));
        Set<String> actual = new HashSet<>();
        before.forEach(row -> {
            actual.add(row.get("task_type") + "/" + row.get("event"));
            assertEquals(PlatformNotificationScope.ID,row.get("project_id")); assertEquals("site",row.get("project_robot_id"));
            assertEquals("NONE",row.get("test_id")); assertEquals("[]",row.get("receivers"));
            assertEquals(Boolean.TRUE,row.get("enable")); assertEquals(Boolean.TRUE,row.get("use_default_template"));
            assertEquals(Boolean.TRUE,row.get("use_default_subject")); assertEquals("",row.get("subject"));
            assertEquals("",jdbc.queryForObject("SELECT template FROM message_task_blob WHERE id=?",String.class,row.get("id")));
        });
        assertEquals(Set.of("BUG_TASK/AT","BUG_TASK/REPLY","FUNCTIONAL_CASE_TASK/AT","FUNCTIONAL_CASE_TASK/REPLY","FUNCTIONAL_CASE_TASK/REVIEW_AT","FUNCTIONAL_CASE_TASK/EXECUTE_AT"),actual);
    }
    @Test void repeatDoesNotOverwriteDisabledRobotOrExistingRuleAndTemplate() {
        initializer.prepare();
        jdbc.update("UPDATE project_robot SET enable=0");
        jdbc.update("UPDATE message_task SET enable=0,receivers='[\"member\"]',subject='kept',use_default_subject=0 WHERE id='msp-platform-built-in-0'");
        jdbc.update("UPDATE message_task_blob SET template='kept' WHERE id='msp-platform-built-in-0'");
        var before=snapshot(); initializer.prepare();
        assertEquals(before,snapshot()); assertFalse(jdbc.queryForObject("SELECT enable FROM project_robot",Boolean.class));
        assertEquals("kept",jdbc.queryForObject("SELECT template FROM message_task_blob WHERE id='msp-platform-built-in-0'",String.class));
    }
    @Test void incompleteAndDuplicateRulesOrMissingAssociationFailWithoutRepair() {
        initializer.prepare();
        jdbc.update("DELETE FROM message_task_blob WHERE id='msp-platform-built-in-0'");
        var before=snapshot(); assertThrows(IllegalStateException.class,initializer::prepare); assertEquals(before,snapshot());
        jdbc.update("INSERT INTO message_task_blob VALUES ('msp-platform-built-in-0','')");
        jdbc.update("UPDATE message_task SET event='REPLY' WHERE id='msp-platform-built-in-0'");
        before=snapshot(); assertThrows(IllegalStateException.class,initializer::prepare); assertEquals(before,snapshot());
        jdbc.update("DELETE FROM message_task WHERE id='msp-platform-built-in-0'");
        before=snapshot(); assertThrows(IllegalStateException.class,initializer::prepare); assertEquals(before,snapshot());
    }
    @Test void wrongRobotOrWrongTestBindingFailsWithoutOverwriting() {
        initializer.prepare(); jdbc.update("UPDATE message_task SET project_robot_id='mail' WHERE id='msp-platform-built-in-0'");
        var before=snapshot(); assertThrows(IllegalStateException.class,initializer::prepare); assertEquals(before,snapshot());
        jdbc.update("UPDATE message_task SET project_robot_id='site',test_id='other' WHERE id='msp-platform-built-in-0'");
        before=snapshot(); assertThrows(IllegalStateException.class,initializer::prepare); assertEquals(before,snapshot());
    }
    @Test void midInsertFailureRollsBackAllSixAndExplicitRetryCanSucceed() {
        jdbc.execute("ALTER TABLE message_task_blob ADD CONSTRAINT fail_insert CHECK (id <> 'msp-platform-built-in-2')");
        assertThrows(RuntimeException.class,initializer::prepare);
        assertEquals(0,count("message_task")); assertEquals(0,count("message_task_blob")); assertEquals(1,count("project_robot"));
        jdbc.execute("ALTER TABLE message_task_blob DROP CONSTRAINT fail_insert"); initializer.prepare(); assertEquals(6,count("message_task"));
    }
    @Test void ambiguousDefaultRobotFailsBeforeAnyTaskWrite() {
        jdbc.update("INSERT INTO project_robot SELECT 'duplicate',project_id,name,platform,webhook,type,app_key,app_secret,enable,create_user,create_time,update_user,update_time,description FROM project_robot");
        assertThrows(IllegalStateException.class,initializer::prepare); assertEquals(0,count("message_task"));
    }
    @Test void simultaneousPreparationKeepsOneSet() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Void> prepare=()->{start.await();initializer.prepare();return null;};
            var a=pool.submit(prepare);var b=pool.submit(prepare);start.countDown();
            a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        assertEquals(6,count("message_task"));assertEquals(6,count("message_task_blob"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"BUG_TASK,AT","BUG_TASK,REPLY","FUNCTIONAL_CASE_TASK,AT","FUNCTIONAL_CASE_TASK,REPLY","FUNCTIONAL_CASE_TASK,REVIEW_AT","FUNCTIONAL_CASE_TASK,EXECUTE_AT"})
    void eachBuiltInEventUsesPlatformRulesAndSourceRecipientsWithoutFallback(String type,String event) throws Exception {
        initializer.prepare();
        var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("test",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),jdbc.getDataSource()));
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        for(String mapper : List.of("MessageTaskMapper","MessageTaskBlobMapper","ProjectRobotMapper")) {
            String resource="io/metersphere/project/mapper/"+mapper+".xml";
            try(var stream=new ClassPathResource(resource).getInputStream()) {
                new org.apache.ibatis.builder.xml.XMLMapperBuilder(stream,config,resource,config.getSqlFragments()).parse();
            }
        }
        try(var session=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config).openSession(true)) {
            var details=new io.metersphere.system.service.MessageDetailService();
            org.springframework.test.util.ReflectionTestUtils.setField(details,"messageTaskMapper",session.getMapper(io.metersphere.project.mapper.MessageTaskMapper.class));
            org.springframework.test.util.ReflectionTestUtils.setField(details,"messageTaskBlobMapper",session.getMapper(io.metersphere.project.mapper.MessageTaskBlobMapper.class));
            org.springframework.test.util.ReflectionTestUtils.setField(details,"projectRobotMapper",session.getMapper(io.metersphere.project.mapper.ProjectRobotMapper.class));
            var members=org.mockito.Mockito.mock(io.metersphere.system.mapper.ExtSystemProjectMapper.class);
            org.mockito.Mockito.when(members.getEnableProjectMemberByUserId(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyList()))
                    .thenAnswer(call -> {
                        String project=call.getArgument(0);List<String> requested=call.getArgument(1);
                        return List.of("A".equals(project)?"alice":"bob","operator").stream().filter(requested::contains).map(id->{
                            var user=new io.metersphere.system.domain.User();user.setId(id);user.setEnable(true);return user;
                        }).toList();
                    });
            var site=new io.metersphere.system.notice.sender.impl.InSiteNoticeSender();
            org.springframework.test.util.ReflectionTestUtils.setField(site,"extSystemProjectMapper",members);
            var sender=new io.metersphere.system.service.NoticeSendService();
            org.springframework.test.util.ReflectionTestUtils.setField(sender,"messageDetailService",details);
            org.springframework.test.util.ReflectionTestUtils.setField(sender,"inSiteNoticeSender",site);
            var previous=(org.springframework.context.MessageSource)org.springframework.test.util.ReflectionTestUtils.getField(io.metersphere.sdk.util.Translator.class,"messageSource");
            new io.metersphere.sdk.util.Translator().setMessageSource(new org.springframework.context.support.StaticMessageSource());
            try {
                // A legacy project rule must never participate in new platform selection.
                jdbc.update("INSERT INTO message_task SELECT 'legacy',event,receivers,project_robot_id,task_type,test_id,'A',enable,create_user,create_time,update_user,update_time,use_default_template,use_default_subject,subject FROM message_task WHERE task_type=? AND event=?",type,event);
                jdbc.update("INSERT INTO message_task_blob VALUES ('legacy','legacy template')");
                for(String project:List.of("A","B")) {
                    var model=io.metersphere.system.notice.NoticeModel.builder().event(event).operator("operator").excludeSelf(true)
                            .relatedUsers(List.of("alice","bob","operator","disabled","deleted")).paramMap(new HashMap<>(Map.of("projectId",project))).build();
                    var deliveries=sender.prepareDeliveries(type,model);
                    assertEquals(1,deliveries.size());var delivery=deliveries.getFirst();
                    assertEquals("A".equals(project)?"alice":"bob",delivery.model().getReceivers().getFirst().getUserId());
                    assertEquals(project,delivery.configuration().getProjectId()); assertTrue(delivery.model().isFrozenRecipients());
                    assertEquals(type,delivery.configuration().getTaskType());assertEquals(event,delivery.configuration().getEvent());
                    assertEquals("IN_SITE",delivery.configuration().getType());assertNotNull(delivery.configuration().getTemplate());
                    model.setRelatedUsers(List.of()); assertTrue(sender.prepareDeliveries(type,model).isEmpty());
                    model.setRelatedUsers(List.of("disabled","deleted","outsider"));assertTrue(sender.prepareDeliveries(type,model).isEmpty());
                    model.setRelatedUsers(List.of("operator"));assertTrue(sender.prepareDeliveries(type,model).isEmpty());
                    model.setRelatedUsers(List.of("A".equals(project)?"alice":"bob"));
                    jdbc.update("UPDATE project_robot SET enable=0 WHERE id='site'");initializer.prepare();
                    assertTrue(sender.prepareDeliveries(type,model).isEmpty());
                    jdbc.update("UPDATE project_robot SET enable=1 WHERE id='site'");
                    assertEquals(1,sender.prepareDeliveries(type,model).size());
                    // Restoring a robot only affects the new call; no sending occurs during preparation.
                }
                org.mockito.Mockito.verify(members,org.mockito.Mockito.never()).getProjectMemberByUserId(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyList());
            } finally { new io.metersphere.sdk.util.Translator().setMessageSource(previous); }
        }
    }
}
