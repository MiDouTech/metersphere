package io.metersphere.agent.service;

import io.metersphere.functional.planreview.*;
import io.metersphere.functional.service.PlanReviewNoticeChannelAdapter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native inbox schema/receipt and current database authorization, no external delivery services. */
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class PlanReviewNoticeChannelMySqlTests {
    ImportFollowupCoordinatorMySqlTests fixture;
    PlanReviewNoticeChannelAdapter channel;
    PlanReviewNoticeService service;
    @BeforeEach void setup() throws Exception {
        fixture=new ImportFollowupCoordinatorMySqlTests();fixture.setup();var jdbc=fixture.jdbc;
        jdbc.execute("ALTER TABLE test_plan ADD create_user VARCHAR(50)");jdbc.execute("ALTER TABLE case_review ADD create_user VARCHAR(50)");
        jdbc.execute("CREATE TABLE user(id VARCHAR(50) PRIMARY KEY,enable BOOLEAN,deleted BOOLEAN,name VARCHAR(100) DEFAULT 'Test account')");
        jdbc.execute("CREATE TABLE user_extend(id VARCHAR(50) PRIMARY KEY,avatar VARCHAR(100))");
        jdbc.execute("CREATE TABLE user_role(id VARCHAR(50) PRIMARY KEY,type VARCHAR(30),enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role_relation(user_id VARCHAR(50),role_id VARCHAR(50),source_id VARCHAR(50))");
        jdbc.execute("CREATE TABLE user_role_permission(role_id VARCHAR(50),permission_id VARCHAR(100))");
        jdbc.execute("CREATE TABLE notification(id BIGINT AUTO_INCREMENT PRIMARY KEY,type VARCHAR(30) NOT NULL,receiver VARCHAR(50) NOT NULL,subject VARCHAR(255) NOT NULL,status VARCHAR(30) NOT NULL,create_time BIGINT NOT NULL,operator VARCHAR(50) NOT NULL,operation VARCHAR(50) NOT NULL,resource_id VARCHAR(50) NOT NULL,project_id VARCHAR(50) NOT NULL,organization_id VARCHAR(50) NOT NULL,resource_type VARCHAR(50) NOT NULL,resource_name VARCHAR(255) NOT NULL,content TEXT NOT NULL)");
        jdbc.update("INSERT INTO user_role VALUES('reader','PROJECT',true),('project_admin','PROJECT',true),('grant','PROJECT',true)");
        for(String role:List.of("reader","project_admin"))jdbc.update("INSERT INTO user_role_permission VALUES(?,'PROJECT_TEST_PLAN:READ')",role);
        jdbc.update("INSERT INTO user_role_permission VALUES('grant','PLAN_REVIEW:GRANT_MANAGE')");
        for(String actor:List.of("creator","source","plan-maker","review-maker","owner","grant-manager")){
            jdbc.update("INSERT INTO user(id,enable,deleted) VALUES(?,true,false)",actor);jdbc.update("INSERT INTO user_role_relation VALUES(?,?,'project')",actor,"owner".equals(actor)?"project_admin":"grant-manager".equals(actor)?"grant":"reader");
        }
        jdbc.update("INSERT INTO test_plan(id,project_id,status,name,type,create_user) VALUES('private-plan','project','UNDERWAY','Private plan','TEST_PLAN','plan-maker')");
        jdbc.update("INSERT INTO case_review VALUES('private-review','project','UNDERWAY','Private review','review-maker')");
        jdbc.update("INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,active_plan_id,active_review_id,state,sync_state,source_version,applied_version,source_changed_at,original_actor,source_actor,create_time,update_time) VALUES('relation','project','private-plan','private-review','generation','private-plan','private-review','ACTIVE','PENDING',1,0,?,'creator','source',1,1)",System.currentTimeMillis()-40000);
        var authorization=new PlanReviewAuthorization(jdbc);channel=new PlanReviewNoticeChannelAdapter(jdbc,authorization);
        service=new PlanReviewNoticeService(jdbc,authorization,channel,fixture.tx.getTransactionManager());
    }
    @AfterEach void cleanup(){if(fixture!=null)fixture.cleanup();}
    @Test void initialCohortIncludesActualCreatorsLatestSourceActorAndExplicitOwnersOnly(){
        var relation=fixture.jdbc.queryForMap("SELECT * FROM plan_review_relation");
        assertEquals(Set.of("creator","source","plan-maker","review-maker","owner"),channel.recipients(relation,"DELAYED"));
        assertEquals(Set.of("owner","grant-manager"),channel.responsibles("project","GRANT_REVOKED"));
        fixture.jdbc.update("UPDATE user SET enable=false WHERE id='owner'");
        fixture.tx.executeWithoutResult(status->assertEquals("ACCOUNT_OR_PROJECT_ACCESS_UNAVAILABLE",channel.eligibility("project","owner")));
    }
    @Test void realInboxIsReadableOnceWithoutCopyingProtectedObjectsOrProjectMetadata(){
        service.drain();service.drain();
        assertEquals(5,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM notification",Integer.class));
        assertEquals(5,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_notice WHERE readable_at IS NOT NULL AND inbox_id IS NOT NULL",Integer.class));
        for(var row:fixture.jdbc.queryForList("SELECT * FROM notification")){
            assertEquals("",row.get("resource_id"));assertEquals("",row.get("project_id"));assertEquals("",row.get("organization_id"));assertEquals("",row.get("resource_name"));
            String content=(String)row.get("content");assertFalse(content.contains("private-plan"));assertFalse(content.contains("Private plan"));assertFalse(content.contains("Private review"));
        }
        fixture.jdbc.update("DELETE FROM user_role_relation WHERE user_id='source'");
        assertEquals("ACCOUNT_OR_PROJECT_ACCESS_UNAVAILABLE",channel.eligibility("project","source"));
    }
    @Test void nativeInboxQueryReturnsOnlyTheCurrentRecipientEvenWithAForgedReceiver() throws Exception {
        service.drain();
        var environment=new org.apache.ibatis.mapping.Environment("isolated",new org.mybatis.spring.transaction.SpringManagedTransactionFactory(),fixture.jdbc.getDataSource());
        var configuration=new org.apache.ibatis.session.Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        var resource=new org.springframework.core.io.ClassPathResource("io/metersphere/system/mapper/BaseNotificationMapper.xml");
        try(var stream=resource.getInputStream()){new org.apache.ibatis.builder.xml.XMLMapperBuilder(stream,configuration,resource.getPath(),configuration.getSqlFragments()).parse();}
        var factory=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration);
        var template=new org.mybatis.spring.SqlSessionTemplate(factory);
        var inbox=new io.metersphere.system.service.NotificationService();
        org.springframework.test.util.ReflectionTestUtils.setField(inbox,"baseNotificationMapper",template.getMapper(io.metersphere.system.mapper.BaseNotificationMapper.class));
        var request=new io.metersphere.system.dto.sdk.request.NotificationRequest();request.setReceiver("owner");
        var rows=inbox.listNotification(request,"creator");
        assertEquals(1,rows.size());assertEquals("creator",rows.getFirst().getReceiver());assertEquals("",rows.getFirst().getResourceId());
        assertEquals("SYSTEM_NOTICE",rows.getFirst().getType());assertEquals("UNREAD",rows.getFirst().getStatus());
    }
}
