package io.metersphere.agent.service;
import io.metersphere.functional.service.FunctionalCaseBodyRepairTool;
import io.metersphere.sdk.util.FunctionalCaseBlobCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class FunctionalCaseBodyRepairMySqlTests {
    @TempDir Path temporary;
    private static final byte[] BROKEN={80,75,3,4,0};
    private static final byte[] REPLACEMENT="[{\"description\":\"restored from verified source\"}]".getBytes(StandardCharsets.UTF_8);
    private String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private FunctionalCaseBodyRepairTool.Request request(Path backup,boolean apply)throws Exception {
        return new FunctionalCaseBodyRepairTool.Request("c","p","steps","operator",hash(BROKEN),100,REPLACEMENT,"verified-fixture",backup,apply,hash(REPLACEMENT));
    }
    private void withDatabase(Check check)throws Exception {
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only"));
        String schema="body_repair_"+UUID.randomUUID().toString().replace("-","");admin.execute("CREATE DATABASE "+schema);
        try {
            var ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only");
            var jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE `user`(id VARCHAR(50) PRIMARY KEY,enable BOOLEAN)");
            jdbc.execute("CREATE TABLE functional_case(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),case_edit_type VARCHAR(10),update_time BIGINT,update_user VARCHAR(50),deleted BOOLEAN,latest BOOLEAN)");
            jdbc.execute("CREATE TABLE functional_case_blob(id VARCHAR(50) PRIMARY KEY,steps LONGBLOB)");
            jdbc.update("INSERT INTO `user` VALUES ('operator',1)");jdbc.update("INSERT INTO functional_case VALUES ('c','p','STEP',100,'operator',0,1)");
            jdbc.update("INSERT INTO functional_case_blob VALUES ('c',?)",BROKEN);
            try(var connection=ds.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_119__functional_import_effect_outbox.sql"));}
            check.run(ds,jdbc);
        } finally {admin.execute("DROP DATABASE "+schema);}
    }
    interface Check {void run(DriverManagerDataSource ds,JdbcTemplate jdbc)throws Exception;}
    @Test void dryRunAndConflictsNeverWriteOrCreateBackups()throws Exception {
        withDatabase((ds,jdbc)->{
            Path backup=temporary.resolve("dry-backup");
            try(var connection=ds.getConnection()) {
                assertEquals("DRY_RUN",FunctionalCaseBodyRepairTool.repair(connection,request(backup,false)).get("state"));
                var bad=new FunctionalCaseBodyRepairTool.Request("c","p","steps","operator",hash(BROKEN),99,REPLACEMENT,"fixture",backup,true,hash(REPLACEMENT));
                assertThrows(IllegalStateException.class,()->FunctionalCaseBodyRepairTool.repair(connection,bad));
            }
            assertFalse(Files.exists(backup));assertArrayEquals(BROKEN,jdbc.queryForObject("SELECT steps FROM functional_case_blob WHERE id='c'",byte[].class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_effect_outbox",Integer.class));
        });
    }
    @Test void applyRequiresPrivateBackupAndRepeatsWithoutAnotherEvent()throws Exception {
        withDatabase((ds,jdbc)->{
            Path backup=temporary.resolve("apply-backup");
            try(var connection=ds.getConnection()) {
                if(!Files.getFileStore(temporary).supportsFileAttributeView("posix")) {
                    assertThrows(UnsupportedOperationException.class,()->FunctionalCaseBodyRepairTool.repair(connection,request(backup,true)));
                    assertArrayEquals(BROKEN,jdbc.queryForObject("SELECT steps FROM functional_case_blob WHERE id='c'",byte[].class));
                    return;
                }
                var applied=FunctionalCaseBodyRepairTool.repair(connection,request(backup,true));
                assertEquals("APPLIED",applied.get("state"));
                assertEquals("ALREADY_MATCHES",FunctionalCaseBodyRepairTool.repair(connection,request(backup,true)).get("state"));
                assertArrayEquals(REPLACEMENT,(byte[])FunctionalCaseBlobCodec.unpack(jdbc.queryForObject("SELECT steps FROM functional_case_blob WHERE id='c'",byte[].class)));
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_effect_outbox",Integer.class));
                try(var files=Files.list(backup)){var saved=files.toList();assertEquals(1,saved.size());assertTrue(Files.readString(saved.getFirst()).contains(HexFormat.of().formatHex(BROKEN)));}
                long revision=jdbc.queryForObject("SELECT update_time FROM functional_case WHERE id='c'",Long.class);
                var undo=new FunctionalCaseBodyRepairTool.Request("c","p","steps","operator",(String)applied.get("afterHash"),revision,null,"rollback",backup,true,null);
                Path saved=Path.of((String)applied.get("backup"));
                assertEquals("APPLIED",FunctionalCaseBodyRepairTool.rollback(connection,undo,saved).get("state"));
                assertEquals("ALREADY_MATCHES",FunctionalCaseBodyRepairTool.rollback(connection,undo,saved).get("state"));
                assertArrayEquals(BROKEN,jdbc.queryForObject("SELECT steps FROM functional_case_blob WHERE id='c'",byte[].class));
                assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_effect_outbox",Integer.class));
            }
        });
    }
    @Test void failedOutboxInsertRollsBackAndPreparedBackupCanBeRetried()throws Exception {
        withDatabase((ds,jdbc)->{
            org.junit.jupiter.api.Assumptions.assumeTrue(Files.getFileStore(temporary).supportsFileAttributeView("posix"),"Apply/crash recovery runs on target Linux filesystem; Windows fails closed in separate test");
            Path backup=temporary.resolve("retry-backup");jdbc.execute("DROP TABLE functional_import_effect_outbox");
            try(var connection=ds.getConnection()) {
                assertThrows(java.sql.SQLException.class,()->FunctionalCaseBodyRepairTool.repair(connection,request(backup,true)));
                assertArrayEquals(BROKEN,jdbc.queryForObject("SELECT steps FROM functional_case_blob WHERE id='c'",byte[].class));
                assertEquals(100L,jdbc.queryForObject("SELECT update_time FROM functional_case WHERE id='c'",Long.class));
                ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_119__functional_import_effect_outbox.sql"));
                assertEquals("APPLIED",FunctionalCaseBodyRepairTool.repair(connection,request(backup,true)).get("state"));
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_effect_outbox",Integer.class));
            }
        });
    }
}
