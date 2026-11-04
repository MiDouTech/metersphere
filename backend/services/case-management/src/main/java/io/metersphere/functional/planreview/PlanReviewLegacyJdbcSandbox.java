package io.metersphere.functional.planreview;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Runs the real migration program in an explicitly supplied, empty, distinct MySQL schema. */
public final class PlanReviewLegacyJdbcSandbox implements PlanReviewLegacySandbox {
    private final JdbcTemplate primary,isolated;
    public PlanReviewLegacyJdbcSandbox(JdbcTemplate primary,JdbcTemplate isolated){this.primary=primary;this.isolated=isolated;}
    @Override public Map<String,Object> rehearse(String project,String change,Map<String,Object> snapshot,PlanReviewLegacyDecision selection,PlanReviewLegacyRehearsalProgram program){
        String target=identity(isolated),source=identity(primary);
        if(source.equals(target))throw fail("演练与原库相同，拒绝执行");
        if(isolated.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class)!=0)throw fail("演练库非空，不会清空或覆盖已有数据");
        // Metadata-only fixture: no passwords, token material, case content or opinion bodies are copied.
        for(String ddl:List.of(
                "CREATE TABLE user(id VARCHAR(50) PRIMARY KEY,enable BOOLEAN,deleted BOOLEAN)",
                "CREATE TABLE project(id VARCHAR(50) PRIMARY KEY,enable BOOLEAN,deleted BOOLEAN)",
                "CREATE TABLE user_role(id VARCHAR(50) PRIMARY KEY,type VARCHAR(32),enabled BOOLEAN)",
                "CREATE TABLE user_role_relation(user_id VARCHAR(50),role_id VARCHAR(50),source_id VARCHAR(50))",
                "CREATE TABLE user_role_permission(role_id VARCHAR(50),permission_id VARCHAR(100))",
                "CREATE TABLE test_plan(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),type VARCHAR(32),status VARCHAR(32),num BIGINT DEFAULT 1,name VARCHAR(255) DEFAULT 'isolated-plan',update_time BIGINT DEFAULT 0)",
                "CREATE TABLE case_review(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),status VARCHAR(32),num BIGINT DEFAULT 1,name VARCHAR(255) DEFAULT 'isolated-review',case_count INT DEFAULT 0,pass_rate DECIMAL(5,2) DEFAULT 0,update_time BIGINT DEFAULT 0)",
                "CREATE TABLE functional_case(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),deleted BOOLEAN)",
                "CREATE TABLE test_plan_functional_case(id VARCHAR(50) PRIMARY KEY,test_plan_id VARCHAR(50),functional_case_id VARCHAR(50))",
                "CREATE TABLE case_review_functional_case(id VARCHAR(50) PRIMARY KEY,review_id VARCHAR(50),case_id VARCHAR(50),status VARCHAR(64),create_time BIGINT,create_user VARCHAR(50),update_time BIGINT,pos BIGINT,UNIQUE(review_id,case_id))",
                "CREATE TABLE case_review_history(id VARCHAR(50) PRIMARY KEY,review_id VARCHAR(50),case_id VARCHAR(50),status VARCHAR(64),content BLOB,deleted BOOLEAN,abandoned BOOLEAN)",
                "CREATE TABLE case_review_user(review_id VARCHAR(50),user_id VARCHAR(50),PRIMARY KEY(review_id,user_id))",
                "CREATE TABLE case_review_functional_case_user(review_id VARCHAR(50),case_id VARCHAR(50),user_id VARCHAR(50),UNIQUE(review_id,case_id,user_id))"))isolated.execute(ddl);
        var tables=new ArrayList<>(List.of("functional_import_followup_group","functional_import_followup","functional_import_followup_item"));
        tables.addAll(primary.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'plan\\_review\\_%' ORDER BY table_name",String.class));
        for(String table:tables){
            if(!table.matches("[a-z_]+"))throw fail("不支持的演练结构名称");
            var definition=primary.queryForMap("SHOW CREATE TABLE "+table);isolated.execute((String)definition.get("Create Table"));
        }
        isolated.update("INSERT INTO project VALUES(?,1,0)",project);isolated.update("INSERT INTO plan_review_grant(project_id,enabled,update_time) VALUES(?,1,0)",project);
        var data=map(snapshot.get("data"));
        for(var row:rows(data,"unifiedRelations"))insert("plan_review_relation",row);
        for(var row:rows(data,"groups"))insert("functional_import_followup_group",row);
        for(var row:rows(data,"jobs"))insert("functional_import_followup",row);
        for(var row:rows(data,"plans"))insert("test_plan",select(row,"id","project_id","type","status"));
        for(var row:rows(data,"reviews"))insert("case_review",row);
        for(var row:rows(data,"sourceMembers")){
            if(row.get("case_project_id")!=null)isolated.update("INSERT IGNORE INTO functional_case(id,project_id,deleted) VALUES(?,?,?)",row.get("functional_case_id"),row.get("case_project_id"),row.get("deleted"));
            insert("test_plan_functional_case",select(row,"id","test_plan_id","functional_case_id"));
        }
        for(var row:rows(data,"reviewMembers"))insert("case_review_functional_case",row);
        for(var row:rows(data,"reviewers"))insert("case_review_user",row);
        for(var row:rows(data,"opinionReferences")){
            var fixture=select(row,"id","review_id","case_id","status","deleted","abandoned");
            fixture.put("content",Objects.toString(row.get("content_hash"),"").getBytes(java.nio.charset.StandardCharsets.UTF_8));insert("case_review_history",fixture);
        }
        for(var row:rows(data,"watermarks"))insert("functional_import_followup_item",row);
        var outcome=program.run(isolated,snapshot,selection);
        if(!Boolean.TRUE.equals(outcome.get("passed")))throw fail("实际迁移演练未通过");
        return outcome;
    }
    private void insert(String table,Map<String,Object> row){var columns=new TreeSet<>(row.keySet());isolated.update("INSERT INTO "+table+"("+String.join(",",columns)+") VALUES("+String.join(",",Collections.nCopies(columns.size(),"?"))+")",columns.stream().map(row::get).toArray());}
    private static Map<String,Object> select(Map<String,Object> row,String... fields){var result=new TreeMap<String,Object>();for(String field:fields)result.put(field,row.get(field));return result;}
    private static String identity(JdbcTemplate jdbc){return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)connection->{
        if(!"MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()))throw fail("隔离演练必须使用真实MySQL");
        try(var statement=connection.createStatement();var result=statement.executeQuery("SELECT CONCAT(@@server_uuid,':',DATABASE())")){result.next();String identity=result.getString(1);if(identity.endsWith(":"))throw fail("演练必须明确指定数据库");return identity;}
    });}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Map<String,Object> value,String key){return (List<Map<String,Object>>)value.get(key);}
    private static PlanReviewFailure fail(String message){return new PlanReviewFailure("ISOLATED_REHEARSAL_UNAVAILABLE",409,message);}
}
