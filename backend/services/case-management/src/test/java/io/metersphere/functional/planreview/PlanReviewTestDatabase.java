package io.metersphere.functional.planreview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

final class PlanReviewTestDatabase {
    private static final java.util.List<String> mysqlSchemas=new java.util.concurrent.CopyOnWriteArrayList<>();
    private static java.util.Properties mysqlProperties;
    private static synchronized DriverManagerDataSource mysql() throws Exception {
        if(mysqlProperties==null){
            mysqlProperties=new java.util.Properties();
            try(var reader=Files.newBufferedReader(Path.of(System.getProperty("plan.review.mysql.config")))){mysqlProperties.load(reader);}
            if(!mysqlProperties.getProperty("url","").startsWith("jdbc:mysql://127.0.0.1:") || !"true".equals(mysqlProperties.getProperty("isolated")))throw new IllegalStateException("Only isolated loopback test instances are permitted");
            Runtime.getRuntime().addShutdownHook(new Thread(()->{
                try{var admin=new JdbcTemplate(mysqlAdmin());verifyMysql(admin);for(String schema:mysqlSchemas)if(schema.matches("msp12_pr_[a-f0-9]{32}"))admin.execute("DROP DATABASE `"+schema+"`");}
                catch(Exception error){System.err.println("Isolated MySQL schema cleanup pending");}
            }));
        }
        var admin=new JdbcTemplate(mysqlAdmin());verifyMysql(admin);
        String schema="msp12_pr_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");mysqlSchemas.add(schema);
        String base=mysqlProperties.getProperty("url");int options=base.indexOf('?');
        return new DriverManagerDataSource(base.substring(0,options)+schema+base.substring(options),mysqlProperties.getProperty("user"),mysqlProperties.getProperty("password"));
    }
    private static DriverManagerDataSource mysqlAdmin(){return new DriverManagerDataSource(mysqlProperties.getProperty("url"),mysqlProperties.getProperty("user"),mysqlProperties.getProperty("password"));}
    private static void verifyMysql(JdbcTemplate admin){if(!java.util.Objects.equals(mysqlProperties.getProperty("serverUuid"),admin.queryForObject("SELECT @@server_uuid",String.class)))throw new IllegalStateException("Isolated server identity mismatch");}
    static JdbcTemplate emptyMysql() throws Exception {return new JdbcTemplate(mysql());}
    record Database(JdbcTemplate jdbc, TransactionTemplate tx, PlanReviewPersistence store,
                    DataSourceTransactionManager manager) { }

    static Database create() throws Exception {
        boolean mysql=System.getProperty("plan.review.mysql.config")!=null;
        var dataSource = mysql?mysql():new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        var manager = new DataSourceTransactionManager(dataSource);
        var tx = new TransactionTemplate(manager);
        jdbc.execute("CREATE TABLE functional_import_followup_group(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),plan_id VARCHAR(50),review_id VARCHAR(50),revision BIGINT DEFAULT 1,create_user VARCHAR(50),create_time BIGINT DEFAULT 0)");
        jdbc.execute("CREATE TABLE functional_import_followup(job_id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),group_id VARCHAR(50),plan_id VARCHAR(50),review_id VARCHAR(50),policy VARCHAR(32),state VARCHAR(32),user_id VARCHAR(50),request_json MEDIUMTEXT,create_time BIGINT DEFAULT 0,update_time BIGINT DEFAULT 0,source_kind VARCHAR(32))");
        jdbc.execute("CREATE TABLE functional_import_followup_item(job_id VARCHAR(50),case_id VARCHAR(50),plan_done BOOLEAN,review_done BOOLEAN)");
        jdbc.execute("CREATE TABLE test_plan(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),type VARCHAR(32),status VARCHAR(32),num BIGINT DEFAULT 1,name VARCHAR(255) DEFAULT 'plan',update_time BIGINT DEFAULT 1)");
        jdbc.execute("CREATE TABLE case_review_module(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50))");
        jdbc.execute("CREATE TABLE test_plan_module(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50))");
        jdbc.execute("CREATE TABLE functional_case(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),deleted BOOLEAN,num BIGINT DEFAULT 1,name VARCHAR(255) DEFAULT 'case-name')");
        jdbc.execute("CREATE TABLE test_plan_functional_case(id VARCHAR(50) PRIMARY KEY,test_plan_id VARCHAR(50),functional_case_id VARCHAR(50))");
        jdbc.execute("CREATE TABLE case_review(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),status VARCHAR(32),num BIGINT DEFAULT 1,name VARCHAR(255) DEFAULT 'review',update_time BIGINT DEFAULT 1,case_count INT DEFAULT 0,pass_rate DECIMAL(5,2) DEFAULT 0)");
        jdbc.execute("CREATE TABLE case_review_functional_case(id VARCHAR(50) PRIMARY KEY,review_id VARCHAR(50),case_id VARCHAR(50),status VARCHAR(64),create_time BIGINT,create_user VARCHAR(50),update_time BIGINT,pos BIGINT,UNIQUE(review_id,case_id))");
        jdbc.execute("CREATE TABLE case_review_user(review_id VARCHAR(50),user_id VARCHAR(50),PRIMARY KEY(review_id,user_id))");
        jdbc.execute("CREATE TABLE case_review_functional_case_user(review_id VARCHAR(50),case_id VARCHAR(50),user_id VARCHAR(50),UNIQUE(review_id,case_id,user_id))");
        jdbc.execute("CREATE TABLE case_review_history(id VARCHAR(50) PRIMARY KEY,review_id VARCHAR(50),case_id VARCHAR(50),status VARCHAR(64),content BLOB,deleted BOOLEAN DEFAULT false,abandoned BOOLEAN DEFAULT false)");
        String schema = Files.readString(Path.of(System.getProperty("plan.review.repo.root"),
                "backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_137__plan_review_relation.sql"));
        if(!mysql)schema = schema.replace(" COLLATE utf8mb4_bin", "").replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String statement : schema.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        for (String migration : java.util.List.of("V3.7.2_138__plan_review_followup_v2.sql", "V3.7.2_139__plan_review_lifecycle.sql", "V3.7.2_140__plan_review_takeover.sql", "V3.7.2_141__plan_review_copy_request.sql", "V3.7.2_142__plan_review_notice_round.sql", "V3.7.2_143__plan_review_legacy_migration.sql")) {
            String next = Files.readString(Path.of(System.getProperty("plan.review.repo.root"), "backend/framework/domain/src/main/resources/migration/3.7.2/ddl", migration));
            if(!mysql)next=next.replace(" COLLATE utf8mb4_bin", "").replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
            for (String statement : next.replaceAll("(?m)^--.*$", "").split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        }
        jdbc.update("INSERT INTO test_plan(id,project_id,type,status) VALUES('plan','project','TEST_PLAN','UNDERWAY')");
        jdbc.update("INSERT INTO case_review(id,project_id,status) VALUES('review','project','UNDERWAY')");
        jdbc.update("INSERT INTO functional_case(id,project_id,deleted) VALUES('a','project',false),('b','project',false),('foreign','other',false)");
        return new Database(jdbc, tx, new PlanReviewPersistence(jdbc), manager);
    }
}
