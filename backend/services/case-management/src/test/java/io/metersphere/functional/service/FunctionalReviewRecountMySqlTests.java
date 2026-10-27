package io.metersphere.functional.service;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Isolated MySQL only; never targets the configured application database. */
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class FunctionalReviewRecountMySqlTests {
 private final String schema="review_recount_"+UUID.randomUUID().toString().replace("-","");
 private JdbcTemplate admin,jdbc;private FunctionalReviewRecountService service;private TransactionTemplate tx;
 @BeforeEach void setup() throws Exception {
  String server="jdbc:mysql://127.0.0.1:13326/",options="?useSSL=false&allowPublicKeyRetrieval=true";
  admin=new JdbcTemplate(new DriverManagerDataSource(server+options,"root","quality-test-only"));
  admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4");
  var source=new DriverManagerDataSource(server+schema+options,"root","quality-test-only");jdbc=new JdbcTemplate(source);
  var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);service=new FunctionalReviewRecountService(jdbc,manager);
  try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_127__review_recount_queue.sql"));}
  jdbc.execute("CREATE TABLE functional_case(id VARCHAR(50) PRIMARY KEY,deleted BOOLEAN)");
  jdbc.execute("CREATE TABLE case_review(id VARCHAR(50) PRIMARY KEY,status VARCHAR(30),case_count INT,pass_rate DECIMAL(6,2))");
  jdbc.execute("CREATE TABLE case_review_functional_case(id VARCHAR(50) PRIMARY KEY,review_id VARCHAR(50),case_id VARCHAR(50),status VARCHAR(30))");
  jdbc.update("INSERT INTO case_review VALUES ('review','UNDERWAY',2,50)");
  jdbc.update("INSERT INTO functional_case VALUES ('first',0),('second',0)");
  jdbc.update("INSERT INTO case_review_functional_case VALUES ('r1','review','first','PASS'),('r2','review','second','UN_REVIEWED')");
 }
 @AfterEach void cleanup(){if(admin!=null&&schema.matches("review_recount_[a-f0-9]{32}"))admin.execute("DROP DATABASE "+schema);}
 @Test void softDeleteRestoreAndReplayUseCurrentState(){
  tx.executeWithoutResult(status->{service.enqueueForCases(List.of("second"));jdbc.update("UPDATE functional_case SET deleted=1 WHERE id='second'");});
  service.drain();assertEquals(1,jdbc.queryForObject("SELECT case_count FROM case_review",Integer.class));
  assertEquals(100,jdbc.queryForObject("SELECT pass_rate FROM case_review",Integer.class));
  jdbc.update("UPDATE functional_case SET deleted=0 WHERE id='second'");service.enqueueForCases(List.of("second"));service.drain();
  assertEquals(2,jdbc.queryForObject("SELECT case_count FROM case_review",Integer.class));
  assertEquals(50,jdbc.queryForObject("SELECT pass_rate FROM case_review",Integer.class));
  service.enqueueForCases(List.of("first","second"));service.drain();
  assertEquals(2,jdbc.queryForObject("SELECT case_count FROM case_review",Integer.class));
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM case_review_functional_case",Integer.class));
 }
 @Test void rollbackDoesNotLeaveWorkAndArchiveStatusIsPreserved(){
  tx.executeWithoutResult(status->{service.enqueueForCases(List.of("first"));status.setRollbackOnly();});
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_review_recount",Integer.class));
  jdbc.update("UPDATE case_review SET status='ARCHIVED'");jdbc.update("UPDATE functional_case SET deleted=1");service.enqueueForCases(List.of("first"));service.drain();
  assertEquals("ARCHIVED",jdbc.queryForObject("SELECT status FROM case_review",String.class));
  assertEquals(0,jdbc.queryForObject("SELECT case_count FROM case_review",Integer.class));
 }
}
