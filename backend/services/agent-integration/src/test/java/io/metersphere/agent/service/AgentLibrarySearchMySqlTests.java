package io.metersphere.agent.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import io.metersphere.functional.mapper.ExtFunctionalCaseMapper;
import io.metersphere.functional.request.FunctionalCasePageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "quality.mysql", matches = "true")
class AgentLibrarySearchMySqlTests {
    @Test void realMapperCountsBeforePagingAndFiltersPriorityTagsAndVersions() throws Exception {
        String schema="library_stability_"+java.util.UUID.randomUUID().toString().replace("-","");
        var admin=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only"));
        admin.execute("CREATE DATABASE "+schema);
        var ds = new SingleConnectionDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true", "root", "quality-test-only", true);
        try {
            var jdbc = new JdbcTemplate(ds);
            jdbc.execute("""
                CREATE TABLE functional_case(id VARCHAR(64), num BIGINT, name VARCHAR(255), module_id VARCHAR(64),
                version_id VARCHAR(64), case_edit_type VARCHAR(32), create_user VARCHAR(64), create_time BIGINT,
                update_user VARCHAR(64), update_time BIGINT, review_status VARCHAR(32), last_execute_result VARCHAR(32),
                execute_user VARCHAR(64), last_execute_user VARCHAR(64), last_execute_time BIGINT, delete_user VARCHAR(64),
                delete_time BIGINT, tags TEXT, workspace_id VARCHAR(64), project_id VARCHAR(64), system_id VARCHAR(64),
                system_module_id VARCHAR(64), ai_create BOOLEAN, deleted BOOLEAN, latest BOOLEAN, ref_id VARCHAR(64), pos BIGINT)
                """);
            jdbc.execute("CREATE TABLE project_version(id VARCHAR(64),name VARCHAR(64))");
            jdbc.execute("CREATE TABLE functional_case_module(id VARCHAR(64),name VARCHAR(64))");
            jdbc.execute("CREATE TABLE project(id VARCHAR(64) PRIMARY KEY,organization_id VARCHAR(64))");
            jdbc.execute("INSERT INTO project VALUES ('p','o'),('outside','other')");
            jdbc.execute("CREATE TABLE test_asset_metadata(organization_id VARCHAR(64),project_id VARCHAR(64),asset_type VARCHAR(32),asset_id VARCHAR(64),creation_source VARCHAR(64),category_id VARCHAR(64),UNIQUE KEY uk_test_asset_metadata(organization_id,project_id,asset_type,asset_id))");
            jdbc.execute("CREATE TABLE test_asset_category(id VARCHAR(64),name VARCHAR(64),path VARCHAR(255),deleted BOOLEAN)");
            jdbc.update("INSERT INTO test_asset_metadata VALUES ('o','p','CASE','ref','MANUAL',NULL),('foreign','p','CASE','ref','FOREIGN',NULL)");
            jdbc.execute("CREATE TABLE functional_case_custom_field(case_id VARCHAR(64),field_id VARCHAR(64),value TEXT)");
            jdbc.execute("CREATE TABLE custom_field(id VARCHAR(64),name VARCHAR(64))");
            jdbc.update("INSERT INTO custom_field VALUES ('priority','functional_priority'),('other','other')");
            for (int i=1;i<=7;i++) {
                jdbc.update("INSERT INTO functional_case(id,num,name,project_id,tags,deleted,latest,pos,version_id,ref_id) VALUES (?,?,?,'p',?,0,1,?,'v','ref')", "c"+i,i,"case"+i,i%2==0?"[\"red\",\"blue\"]":"[\"red\"]",i);
                jdbc.update("INSERT INTO functional_case_custom_field VALUES (?, 'priority', ?)","c"+i,i<=5?"\"P0\"":"P1");
                jdbc.update("INSERT INTO functional_case_custom_field VALUES (?, 'other', 'P0')","c"+i);
            }
            jdbc.update("INSERT INTO functional_case(id,project_id,tags,deleted,latest,pos) VALUES ('foreign','outside','[\"red\"]',0,1,99),('deleted','p','[\"red\"]',1,1,98),('old','p','[]',0,0,97)");
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(ds);
            factory.setPlugins(new PageInterceptor());
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:io/metersphere/**/mapper/*.xml"));
            try (var session=factory.getObject().openSession()) {
                var mapper=session.getMapper(ExtFunctionalCaseMapper.class);
                var request=new FunctionalCasePageRequest(); request.setProjectId("p");
                request.setFilter(Map.of("agentPriority",List.of("P0"),"agentTagsAny",List.of("blue")));
                try (var page=PageHelper.startPage(1,1,"functional_case.pos desc, functional_case.id asc")) {
                    var rows=mapper.list(request,false,true);
                    assertEquals(2,page.getTotal()); assertEquals(1,rows.size()); assertEquals("c4",rows.getFirst().getId());
                }
                try (var page=PageHelper.startPage(2,1,"functional_case.pos desc, functional_case.id asc")) {
                    assertEquals("c2",mapper.list(request,false,true).getFirst().getId()); assertEquals(2,page.getTotal());
                }
                request.setFilter(Map.of("agentTagsAll",List.of("red","blue")));
                assertEquals(3,mapper.list(request,false,true).size());
                request.setFilter(Map.of("agentTagsAll",List.of("red","missing")));
                assertTrue(mapper.list(request,false,true).isEmpty());
                request.setFilter(Map.of());
                assertEquals(7,mapper.list(request,false,true).size());
                assertEquals("MANUAL",mapper.list(request,false,true).getFirst().getCreationSource());
                request.setCreationSources(List.of("FOREIGN"));
                try(var page=PageHelper.startPage(1,10)) {
                    assertTrue(mapper.list(request,false,true).isEmpty());
                    assertEquals(0,page.getTotal());
                }
                request.setCreationSources(List.of("MANUAL"));
                try(var page=PageHelper.startPage(1,2)) {
                    assertEquals(2,mapper.list(request,false,true).size());
                    assertEquals(7,page.getTotal());
                }
                request.setCreationSources(List.of());
                var countStatement=session.getConfiguration().getMappedStatement(ExtFunctionalCaseMapper.class.getName()+".list_COUNT");
                String countSql=countStatement.getBoundSql(Map.of("request",request,"deleted",false,"isRepeat",true)).getSql();
                assertFalse(countSql.contains("LEFT JOIN"),"display joins must not be counted");
                request.setProjectId("empty"); assertTrue(mapper.list(request,false,true).isEmpty());
            }
        } finally {PageHelper.clearPage(); ds.destroy(); if(schema.matches("library_stability_[a-f0-9]{32}")) admin.execute("DROP DATABASE "+schema);}
    }
}
