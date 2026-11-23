package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentRuntimeRoleAccessTests {
    JdbcTemplate db;
    AgentBusinessAccess access;
    AgentProjectService projects;
    Object guard;
    java.lang.reflect.Method authorize;
    @BeforeEach void setup() throws Exception {
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        db.execute("CREATE TABLE ai_execution_task(id VARCHAR(64),project_id VARCHAR(64))");
        db.execute("CREATE TABLE ai_runner_lease(id VARCHAR(64),task_id VARCHAR(64))");
        db.execute("CREATE TABLE ai_execution_checkpoint(id VARCHAR(64),task_id VARCHAR(64))");
        db.execute("CREATE TABLE ai_execution_preflight(id VARCHAR(64),project_id VARCHAR(64))");
        db.update("INSERT INTO ai_execution_task VALUES('task','project'),('other-task','other')");
        db.update("INSERT INTO ai_runner_lease VALUES('lease','task'),('foreign-lease','other-task')");
        db.update("INSERT INTO ai_execution_checkpoint VALUES('checkpoint','task')");
        db.update("INSERT INTO ai_execution_preflight VALUES('preflight','project')");
        access=mock(AgentBusinessAccess.class); projects=mock(AgentProjectService.class);
        when(projects.resolveProjectId(any())).thenAnswer(call->call.getArgument(0));
        Class<?> type=Class.forName("io.metersphere.agent.service.AgentRuntimeRoleAccess");
        guard=type.getConstructor(JdbcTemplate.class,AgentBusinessAccess.class,AgentProjectService.class).newInstance(db,access,projects);
        authorize=type.getMethod("authorize",AgentMcpToolHandler.class,Map.class);
    }
    private void check(String name, boolean read, Map<String,Object> args) {
        var handler=mock(AgentMcpToolHandler.class); when(handler.name()).thenReturn("metersphere."+name);when(handler.readOnlyHint()).thenReturn(read);
        try { authorize.invoke(guard,handler,args); }
        catch(java.lang.reflect.InvocationTargetException error) {
            if(error.getCause() instanceof RuntimeException runtime)throw runtime;
            throw new AssertionError(error.getCause());
        } catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    @Test void runtimeActionsUseWebEquivalentPermissionsAndActualResourceProject() {
        check("execution.cancel",false,Map.of("executionTaskId","task"));
        verify(access).project("project","AI_EXECUTION:CANCEL",true);
        check("execution.resume",false,Map.of("executionTaskId","task"));
        verify(access).project("project","AI_EXECUTION:LOGIN",true);
        check("execution.complete",false,Map.of("leaseId","lease"));
        verify(access).project("project","AI_EXECUTION:RUN",true);
        check("human_request.respond",false,Map.of("taskId","task"));
        verify(access,times(2)).project("project","AI_EXECUTION:RUN",true);
        check("execution.result.get",true,Map.of("taskId","task"));
        verify(access).project("project","AI_EXECUTION:READ",false);
    }
    @Test void inconsistentProjectTaskOrLeaseCannotAuthorizeAnotherResource() {
        assertThrows(MSException.class,()->check("execution.cancel",false,Map.of("executionTaskId","task","projectId","other")));
        assertThrows(MSException.class,()->check("execution.complete",false,Map.of("taskId","task","leaseId","foreign-lease")));
        assertThrows(MSException.class,()->check("execution.get",true,Map.of("taskId","missing")));
        verifyNoInteractions(access);
    }
    @Test void everyCallRechecksRoleAndRevocationIsNotHiddenByPriorSuccess() {
        check("execution.cancel",false,Map.of("executionTaskId","task"));
        doThrow(AgentBusinessAccess.denied()).when(access).project("project","AI_EXECUTION:CANCEL",true);
        assertThrows(MSException.class,()->check("execution.cancel",false,Map.of("executionTaskId","task")));
        verify(access,times(2)).project("project","AI_EXECUTION:CANCEL",true);
    }
    @Test void configurationMetadataRequiresTheSameBusinessRoleAsItsWebPage() {
        for(String name:List.of("environment.profile.list","environment.profile.get","page_object.get","business_flow.get"))
            check(name,true,Map.of("projectId","project"));
        verify(access,times(4)).project("project","AI_EXECUTION:READ",false);
        check("credential.metadata.list",true,Map.of("projectId","project"));
        verify(access).project("project","AI_CREDENTIAL:READ_METADATA",false);
    }
}
