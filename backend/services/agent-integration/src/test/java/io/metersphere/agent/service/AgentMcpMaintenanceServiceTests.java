package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.bug.dto.request.BugTransitionRequest;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.plan.domain.*;
import io.metersphere.plan.dto.request.TestPlanUpdateRequest;
import io.metersphere.plan.mapper.*;
import io.metersphere.plan.service.*;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.service.PermissionCheckService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentMcpMaintenanceServiceTests {
    @InjectMocks private AgentMcpMaintenanceService service;
    @Spy private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    @Mock private AgentProjectService projectService;
    @Mock private PermissionCheckService permissionCheckService;
    @Mock private AgentBatchSubmitService batchService;
    @Mock private FunctionalCaseMapper functionalCaseMapper;
    @Mock private TestPlanFunctionalCaseMapper planCaseMapper;
    @Mock private TestPlanMapper planMapper;
    @Mock private TestPlanService planService;
    @Mock private TestPlanFunctionalCaseService planCaseService;
    @Mock private AgentTestPlanWriteService planWriteService;
    @Mock private AgentTaskClaimService taskClaims;
    @Mock private AgentExecutionCheckpointService checkpointService;
    @Mock private AgentExecutionMapper executionMapper;
    @Mock private AgentBugWriteService bugService;
    @Mock private AgentExecLogService execLogService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private TestPlanManagementService planManagementService;

    @BeforeEach void before() {
        AgentToken token = new AgentToken(); token.setId("token"); token.setUserId("actor");
        AgentTokenContext.set(token);
        lenient().when(projectService.resolveProjectId("project")).thenReturn("project");
        lenient().when(permissionCheckService.userHasSourcePermission(eq("actor"),eq("project"),anyString(),eq("PROJECT"))).thenReturn(true);
    }
    @AfterEach void after() { AgentTokenContext.clear(); AgentExecutionActorContext.clear(); }

    @Test void historyIncludesTerminalStatusTimeBoundsAndRealTotal() {
        var request = history(); request.setStatus(" failed "); request.setKeyword("100%_");
        request.setCreatedAfter(100L); request.setCreatedBefore(200L); request.setCurrent(2);
        when(executionMapper.countPersonalHistory(eq("project"),same(request),eq("100!%!_"))).thenReturn(37L);
        when(executionMapper.searchPersonalHistory(eq("project"),same(request),eq("100!%!_"),eq(20))).thenReturn(List.of());
        var result = service.history(request);
        assertEquals(37L,result.getTotal()); assertEquals(2,result.getCurrent()); assertEquals("FAILED",request.getStatus());
    }
    @Test void historyRejectsInvalidRangesAndPaginationBeforeQuery() {
        var request = history(); request.setCreatedAfter(200L);request.setCreatedBefore(100L);
        assertThrows(MSException.class,()->service.history(request));
        request.setCreatedBefore(300L);request.setPageSize(101);
        assertThrows(MSException.class,()->service.history(request));
        verifyNoInteractions(executionMapper);
    }
    @Test void historyCannotBypassProjectOrUserPermission() {
        when(projectService.resolveProjectId("forbidden")).thenThrow(new MSException("PERMISSION_DENIED"));
        var request = history(); request.setProjectId("forbidden");
        assertThrows(MSException.class,()->service.history(request));
        when(permissionCheckService.userHasSourcePermission("actor","project",PermissionConstants.AI_EXECUTION_READ,"PROJECT")).thenReturn(false);
        assertThrows(MSException.class,()->service.history(history())); verifyNoInteractions(executionMapper);
    }
    @Test void transitionsValidateAndDelegateWorkflowVersion() {
        BugTransitionRequest request = new BugTransitionRequest(); request.setTransitionId("t");request.setTargetStatusId("done");request.setExpectedUpdateTime(123L);
        service.transitions("project","bug");service.transition("project","bug",request);
        verify(bugService).getTransitions("project","bug");verify(bugService).transition("project","bug",request);
        request.setExpectedUpdateTime(null);
        assertThrows(MSException.class,()->service.transition("project","bug",request));
    }
    @Test void resumeEnforcesPersonalTaskAndRestoresActorOnFailure() {
        AgentExecutionTaskDTO task = new AgentExecutionTaskDTO();task.setProjectId("project");
        when(taskClaims.getPersonalTask("task")).thenReturn(task);
        var request = resume();
        when(checkpointService.resume("task","cp",request)).thenAnswer(call->{assertEquals("actor",AgentExecutionActorContext.get());throw new MSException("CHECKPOINT_HASH_MISMATCH");});
        AgentExecutionActorContext.bind("prior");
        assertThrows(MSException.class,()->service.resume("task","cp",request));
        assertEquals("prior",AgentExecutionActorContext.get());
        when(taskClaims.getPersonalTask("platform-task")).thenThrow(new MSException("AGENT_TASK_NOT_FOUND"));
        assertThrows(MSException.class,()->service.resume("platform-task","cp",request));
        verify(checkpointService,times(1)).resume(anyString(),anyString(),any());
    }
    @Test void resumeRejectsMissingTokenBeforeService() {
        var request = resume();request.setResumeToken("short");
        assertThrows(MSException.class,()->service.resume("task","cp",request));verifyNoInteractions(taskClaims,checkpointService);
    }
    @Test void batchMergesProjectAndChecksEveryCaseBeforeWriting() {
        var request = batch();
        FunctionalCase row = new FunctionalCase();row.setId("case");row.setProjectId("project");
        when(functionalCaseMapper.selectByPrimaryKey("case")).thenReturn(row);
        service.batch(request,"request");
        assertEquals("project",request.getResults().getFirst().getProjectId());
        verify(batchService).batchSubmit(request,"request");
    }
    @Test void batchRejectsCrossProjectCaseWithoutPartialWrites() {
        var request = batch();FunctionalCase row = new FunctionalCase();row.setProjectId("other-project");
        when(functionalCaseMapper.selectByPrimaryKey("case")).thenReturn(row);
        assertThrows(MSException.class,()->service.batch(request,"request"));verifyNoInteractions(batchService);
    }
    @Test void batchRejectsWrongPlanAssociation() {
        var request = batch();request.setTestPlanId("plan");request.getResults().getFirst().setTestPlanCaseId("relation");
        FunctionalCase row = new FunctionalCase();row.setProjectId("project");
        when(functionalCaseMapper.selectByPrimaryKey("case")).thenReturn(row); plan();
        TestPlanFunctionalCase relation = new TestPlanFunctionalCase();relation.setTestPlanId("another-plan");
        when(planCaseMapper.selectByPrimaryKey("relation")).thenReturn(relation);
        assertThrows(MSException.class,()->service.batch(request,"request"));verifyNoInteractions(batchService);
    }
    @Test void updatePreservesTagsAndDoesNotMovePlanWhenOnlyNameChanges() {
        editable();
        service.updatePlan("project","plan",100L,Map.of("name","renamed"));
        var capture = ArgumentCaptor.forClass(TestPlanUpdateRequest.class);
        verify(planService).update(capture.capture(),eq("actor"),anyString(),eq("POST"));
        assertEquals(Set.of("original"),capture.getValue().getTags());assertNull(capture.getValue().getGroupId());assertNull(capture.getValue().getModuleId());
        var version = ArgumentCaptor.forClass(TestPlan.class);verify(planMapper).updateByPrimaryKeySelective(version.capture());
        assertTrue(version.getValue().getUpdateTime()>100L);
    }
    @Test void updateRejectsConflictInvalidPatchAndReversedSchedule() {
        editable();
        assertThrows(MSException.class,()->service.updatePlan("project","plan",99L,Map.of("name","changed")));
        assertThrows(MSException.class,()->service.updatePlan("project","plan",100L,Map.of("status","ARCHIVED")));
        assertThrows(MSException.class,()->service.updatePlan("project","plan",100L,Map.of("plannedStartTime",200L,"plannedEndTime",100L)));
        verifyNoInteractions(planService);
    }
    @Test void planMaintenanceRejectsArchivedOrExecutingPlan() {
        TestPlan plan = plan();plan.setStatus("ARCHIVED");
        assertThrows(MSException.class,()->service.disassociate("project","plan",List.of("relation")));
        plan.setStatus("NOT_ARCHIVED");
        when(jdbcTemplate.queryForObject(startsWith("SELECT id"),eq(String.class),eq("plan"))).thenReturn("plan");
        when(jdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM test_plan_report"),eq(Long.class),eq("plan"))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM ai_execution_task"),eq(Long.class),eq("plan"))).thenReturn(0L);
        assertThrows(MSException.class,()->service.updatePlan("project","plan",100L,Map.of("name","n")));
        verifyNoInteractions(planService,planCaseService);
    }
    @Test void disassociateRejectsForeignAssociationAndUsesExplicitIdsOnly() {
        editable();TestPlanFunctionalCase relation = new TestPlanFunctionalCase();relation.setTestPlanId("other");
        when(planCaseMapper.selectByPrimaryKey("relation")).thenReturn(relation);
        assertThrows(MSException.class,()->service.disassociate("project","plan",List.of("relation")));
        verifyNoInteractions(planCaseService);
        relation.setTestPlanId("plan");service.disassociate("project","plan",List.of("relation"));
        verify(planCaseService).disassociate(argThat(r -> !r.isSelectAll() && r.getSelectIds().equals(List.of("relation"))),any());
    }
    private AgentExecutionHistoryRequest history(){var r=new AgentExecutionHistoryRequest();r.setProjectId("project");return r;}
    private AgentCheckpointResumeRequest resume(){var r=new AgentCheckpointResumeRequest();r.setResumeToken("a".repeat(43));r.setPreflightId("preflight");return r;}
    private AgentBatchSubmitRequest batch(){var r=new AgentBatchSubmitRequest();r.setProjectId("project");var item=new AgentCaseSubmitRequest();item.setCaseId("case");item.setLastExecResult("SUCCESS");r.setResults(List.of(item));return r;}
    private TestPlan plan(){TestPlan p=new TestPlan();p.setId("plan");p.setProjectId("project");p.setType("TEST_PLAN");p.setStatus("NOT_ARCHIVED");p.setUpdateTime(100L);p.setTags(List.of("original"));when(planMapper.selectByPrimaryKey("plan")).thenReturn(p);return p;}
    private void editable(){plan();lenient().when(jdbcTemplate.queryForObject(startsWith("SELECT COUNT"),eq(Long.class),eq("plan"))).thenReturn(0L);}
}
