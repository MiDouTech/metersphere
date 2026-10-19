package io.metersphere.agent.service;
import io.metersphere.agent.dto.AgentCaseExecutabilityRequest;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class AgentCaseScopeMySqlTests {
    @Test void scopesIdsAndRefsAndValidatesWholeBatchBeforeWriting() {
        var ds=new SingleConnectionDataSource("jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only",true);
        try {
            var jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TEMPORARY TABLE functional_case(id VARCHAR(64),ref_id VARCHAR(64),project_id VARCHAR(64),deleted BOOLEAN,latest BOOLEAN,update_time BIGINT)");
            jdbc.execute("CREATE TEMPORARY TABLE ai_environment_execution_profile(id VARCHAR(64),project_id VARCHAR(64))");
            jdbc.update("INSERT INTO functional_case VALUES ('old','stable','p1',0,0,1),('new','stable','p1',0,1,2),('foreign','outside','p2',0,1,3),('deleted','deleted-ref','p1',1,1,4)");
            jdbc.update("INSERT INTO ai_environment_execution_profile VALUES ('env','p1'),('foreign-env','p2')");
            var service=new AgentCaseExecutabilityService();var projects=mock(AgentProjectService.class);
            when(projects.resolveProjectId("p1")).thenReturn("p1");
            ReflectionTestUtils.setField(service,"jdbc",jdbc);ReflectionTestUtils.setField(service,"projects",projects);
            assertEquals("old",ReflectionTestUtils.invokeMethod(service,"resolveCaseId","p1","old"));
            assertEquals("new",ReflectionTestUtils.invokeMethod(service,"resolveCaseId","p1","stable"));
            for(String supplied:List.of("foreign","outside","deleted","deleted-ref"))assertThrows(MSException.class,()->ReflectionTestUtils.invokeMethod(service,"resolveCaseId","p1",supplied));
            var request=new AgentCaseExecutabilityRequest();request.setProjectId("p1");request.setEnvironmentProfileId("env");request.setCaseIds(List.of("old","foreign"));
            assertEquals("CASE_NOT_FOUND",assertThrows(MSException.class,()->service.batchCheck(request)).getMessage());
            request.setEnvironmentProfileId("foreign-env");
            assertEquals("ENVIRONMENT_PROFILE_NOT_FOUND",assertThrows(MSException.class,()->service.batchCheck(request)).getMessage());
        } finally {ds.destroy();}
    }
}
