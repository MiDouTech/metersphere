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
        var ds = new SingleConnectionDataSource("jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true", "root", "quality-test-only", true);
        try {
            var jdbc = new JdbcTemplate(ds);
            jdbc.execute("""
                CREATE TEMPORARY TABLE functional_case(id VARCHAR(64), num BIGINT, name VARCHAR(255), module_id VARCHAR(64),
                version_id VARCHAR(64), case_edit_type VARCHAR(32), create_user VARCHAR(64), create_time BIGINT,
                update_user VARCHAR(64), update_time BIGINT, review_status VARCHAR(32), last_execute_result VARCHAR(32),
                execute_user VARCHAR(64), last_execute_user VARCHAR(64), last_execute_time BIGINT, delete_user VARCHAR(64),
                delete_time BIGINT, tags TEXT, workspace_id VARCHAR(64), project_id VARCHAR(64), system_id VARCHAR(64),
                system_module_id VARCHAR(64), ai_create BOOLEAN, deleted BOOLEAN, latest BOOLEAN, ref_id VARCHAR(64), pos BIGINT)
                """);
            jdbc.execute("CREATE TEMPORARY TABLE project_version(id VARCHAR(64),name VARCHAR(64))");
            jdbc.execute("CREATE TEMPORARY TABLE functional_case_module(id VARCHAR(64),name VARCHAR(64))");
            jdbc.execute("CREATE TEMPORARY TABLE test_asset_metadata(project_id VARCHAR(64),asset_type VARCHAR(32),asset_id VARCHAR(64),creation_source VARCHAR(64),category_id VARCHAR(64))");
            jdbc.execute("CREATE TEMPORARY TABLE test_asset_category(id VARCHAR(64),name VARCHAR(64),path VARCHAR(255),deleted BOOLEAN)");
            jdbc.execute("CREATE TEMPORARY TABLE functional_case_custom_field(case_id VARCHAR(64),field_id VARCHAR(64),value TEXT)");
            jdbc.execute("CREATE TEMPORARY TABLE custom_field(id VARCHAR(64),name VARCHAR(64))");
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
                request.setProjectId("empty"); assertTrue(mapper.list(request,false,true).isEmpty());
            }
        } finally {PageHelper.clearPage(); ds.destroy();}
    }
}
