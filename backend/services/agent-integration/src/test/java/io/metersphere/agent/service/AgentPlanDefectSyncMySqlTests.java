package io.metersphere.agent.service;

import io.metersphere.plan.service.TestPlanDefectSyncService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class AgentPlanDefectSyncMySqlTests {
    private final String schema="defect_sync_"+UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate admin,jdbc;
    @BeforeEach void setup() {
        admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only"));
        admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only"));
        jdbc.execute("CREATE TABLE functional_case(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),deleted BOOLEAN,latest BOOLEAN)");
        jdbc.execute("CREATE TABLE test_plan_functional_case(id VARCHAR(50) PRIMARY KEY,functional_case_id VARCHAR(50),test_plan_id VARCHAR(50))");
        jdbc.execute("CREATE TABLE test_plan(id VARCHAR(50) PRIMARY KEY, project_id VARCHAR(50), status VARCHAR(20))");
        jdbc.update("INSERT INTO test_plan VALUES ('plan1','p1','NOT_ARCHIVED'),('plan2','p1','NOT_ARCHIVED')");
        jdbc.execute("CREATE TABLE bug(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE bug_relation_case(id VARCHAR(50) PRIMARY KEY,case_id VARCHAR(50),bug_id VARCHAR(50),case_type VARCHAR(64),test_plan_id VARCHAR(50),test_plan_case_id VARCHAR(50),create_user VARCHAR(50),create_time BIGINT,update_time BIGINT,sync_origin VARCHAR(16) DEFAULT 'LEGACY')");
        jdbc.update("INSERT INTO functional_case VALUES ('c1','p1',0,1),('c2','p2',0,1),('deleted','p1',1,1)");
        jdbc.update("INSERT INTO test_plan_functional_case VALUES ('r1','c1','plan1'),('r2','c1','plan2'),('r3','c2','plan1'),('r4','deleted','plan1')");
        jdbc.update("INSERT INTO bug VALUES ('b1','p1',0),('b2','p1',0),('foreign','p2',0),('removed','p1',1)");
        jdbc.update("INSERT INTO bug_relation_case(id,case_id,bug_id,case_type) VALUES ('s1','c1','b1','FUNCTIONAL'),('s2','c1','b1','FUNCTIONAL'),('s3','c1','foreign','FUNCTIONAL'),('s4','deleted','b1','FUNCTIONAL')");
        jdbc.update("INSERT INTO bug_relation_case(id,case_id,bug_id,case_type) VALUES ('s5','c1','removed','FUNCTIONAL')");
        jdbc.update("INSERT INTO bug_relation_case(id,case_id,bug_id,case_type,test_plan_id,test_plan_case_id) VALUES ('manual','c1','b2','FUNCTIONAL','plan1','r1')");
    }
    @AfterEach void cleanup(){if(admin!=null && schema.matches("defect_sync_[a-f0-9]{32}"))admin.execute("DROP DATABASE "+schema);}
    @Test void fillsOnlyMissingSourceLinksAndKeepsManualAndOtherPlans() {
        var service=new TestPlanDefectSyncService(jdbc);
        assertEquals(1,service.sync("p1","plan1","u1","FUNCTIONAL"));
        assertEquals(0,service.sync("p1","plan1","u1","FUNCTIONAL"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM bug_relation_case WHERE test_plan_case_id='r1'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM bug_relation_case WHERE test_plan_case_id IN ('r2','r3','r4')",Integer.class));
        jdbc.update("DELETE FROM bug_relation_case WHERE test_plan_id IS NULL");
        assertEquals(0,service.sync("p1","plan1","u1","FUNCTIONAL"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM bug_relation_case WHERE id='manual'",Integer.class));
    }
}
