package io.metersphere.agent.service;

import io.metersphere.functional.planreview.*;
import io.metersphere.functional.hub.dao.DefaultHubSyncJobDao;
import io.metersphere.plan.hub.dto.DefaultHubPlanImportRequest;
import io.metersphere.plan.hub.service.*;
import io.metersphere.plan.service.PlanReviewCreationJournal;
import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.system.service.DefaultHubProjectService;
import io.metersphere.sdk.constants.*;
import io.metersphere.sdk.util.JSON;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real durable hub job/operation transactions; conflicting target uses the native SKIP path. */
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class PlanReviewHubOperationMySqlTests {
    ImportFollowupCoordinatorMySqlTests fixture;
    DefaultHubPlanImportService target,service;
    PlanReviewOperationService operations;
    PlanReviewCreationJournal.Reservation reservation;
    @BeforeEach void setup() throws Exception {
        fixture=new ImportFollowupCoordinatorMySqlTests();fixture.setup();var jdbc=fixture.jdbc;
        jdbc.execute("ALTER TABLE default_hub_sync_job ADD progress INT DEFAULT 0,ADD success_count INT DEFAULT 0,ADD fail_count INT DEFAULT 0,ADD error_message VARCHAR(2000),ADD update_time BIGINT,ADD finish_time BIGINT");
        jdbc.execute("CREATE TABLE test_plan_module(id VARCHAR(50),project_id VARCHAR(50),parent_id VARCHAR(50))");
        jdbc.update("INSERT INTO test_plan_module VALUES('module','project',?)",ModuleConstants.ROOT_NODE_PARENT_ID);
        jdbc.update("INSERT INTO test_plan(id,project_id,status,name) VALUES('hub-source','hub','PREPARED','Template'),('existing','project','PREPARED','Template')");
        var authorization=mock(PlanReviewAuthorization.class);
        when(authorization.current(anyString(),anyString(),anyBoolean())).thenReturn(new PlanReviewPolicy.Access(EnumSet.allOf(PlanReviewPolicy.Permission.class),true));
        var journalTarget=new PlanReviewCreationJournal();ReflectionTestUtils.setField(journalTarget,"jdbc",jdbc);ReflectionTestUtils.setField(journalTarget,"authorization",authorization);
        var journal=fixture.transactional(journalTarget);
        var request=new DefaultHubPlanImportRequest();request.setSourcePlanId("hub-source");request.setTargetProjectId("project");request.setRequestKey("hub-original");request.setConflictStrategy(DefaultHubConstants.CONFLICT_SKIP);
        request.setReviewSettings(new PlanReviewSettings("Review","root",List.of("reviewer"),"SINGLE",false));
        reservation=journal.prepare("project","user","HUB_IMPORT","CREATE_PAIR","hub-original",JSON.toJSONString(request),request.getReviewSettings(),0);
        jdbc.update("UPDATE plan_review_operation SET result_json=? WHERE id=?",JSON.toJSONString(Map.of("jobId","hub-job")),reservation.id());
        jdbc.update("INSERT INTO default_hub_sync_job(id,scope_project_id,create_user,job_type,status) VALUES('hub-job','project','user',?,?)",DefaultHubConstants.JOB_TYPE_IMPORT_PLAN,DefaultHubConstants.JOB_STATUS_PENDING);
        var projects=mock(DefaultHubProjectService.class);when(projects.getDefaultProjectId()).thenReturn("hub");
        var mapper=mock(TestPlanMapper.class);var existing=new TestPlan();existing.setId("existing");existing.setProjectId("project");when(mapper.selectByExample(any())).thenReturn(List.of(existing));
        var jobs=new DefaultHubSyncJobDao();ReflectionTestUtils.setField(jobs,"jdbcTemplate",jdbc);
        target=new DefaultHubPlanImportService();ReflectionTestUtils.setField(target,"jdbcTemplate",jdbc);ReflectionTestUtils.setField(target,"transactionTemplate",fixture.tx);
        ReflectionTestUtils.setField(target,"creationJournal",journal);ReflectionTestUtils.setField(target,"testPlanMapper",mapper);ReflectionTestUtils.setField(target,"defaultHubProjectService",projects);ReflectionTestUtils.setField(target,"defaultHubSyncJobDao",jobs);
        service=fixture.transactional(target);
        var provider=new PlanReviewHubOperationResumer();ReflectionTestUtils.setField(provider,"imports",service);
        operations=new PlanReviewOperationService(jdbc,authorization,List.of(provider));
    }
    @AfterEach void cleanup(){if(fixture!=null)fixture.cleanup();}
    @Test void recoveryUsesTheOriginalHubJobAndReservationAndSkipRemainsTerminal(){
        fixture.tx.executeWithoutResult(status->operations.resume("project",reservation.id(),0,"resume","user"));
        assertEquals("SKIPPED",fixture.jdbc.queryForObject("SELECT state FROM plan_review_operation",String.class));
        assertEquals(DefaultHubConstants.JOB_STATUS_SUCCESS,fixture.jdbc.queryForObject("SELECT status FROM default_hub_sync_job",String.class));
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM default_hub_sync_job",Integer.class));assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM test_plan WHERE id=?",Integer.class,reservation.planId()));
        fixture.tx.executeWithoutResult(status->operations.resume("project",reservation.id(),1,"another-resume","user"));
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_operation_command",Integer.class));
        assertEquals("hub-job",JSON.parseObject(fixture.jdbc.queryForObject("SELECT result_json FROM plan_review_operation",String.class),Map.class).get("jobId"));
    }
    @Test void staleAsyncFailureCannotOverwriteANewerExecutorOrSuccessfulHubJob(){
        var observed=fixture.jdbc.queryForMap("SELECT * FROM plan_review_operation");
        fixture.jdbc.update("UPDATE plan_review_operation SET executing_actor='new',revision=revision+1");fixture.jdbc.update("UPDATE default_hub_sync_job SET status=?,progress=100",DefaultHubConstants.JOB_STATUS_SUCCESS);
        ReflectionTestUtils.invokeMethod(target,"recordFailure",observed);
        assertEquals("new",fixture.jdbc.queryForObject("SELECT executing_actor FROM plan_review_operation",String.class));
        assertEquals(DefaultHubConstants.JOB_STATUS_SUCCESS,fixture.jdbc.queryForObject("SELECT status FROM default_hub_sync_job",String.class));
        assertEquals(100,fixture.jdbc.queryForObject("SELECT progress FROM default_hub_sync_job",Integer.class));
    }
    @Test void ownedFailureIsDurableSafeAndRecordedOnlyForTheObservedRevision(){
        var observed=fixture.jdbc.queryForMap("SELECT * FROM plan_review_operation");
        ReflectionTestUtils.invokeMethod(target,"recordFailure",observed);ReflectionTestUtils.invokeMethod(target,"recordFailure",observed);
        assertEquals("FAILED",fixture.jdbc.queryForObject("SELECT state FROM plan_review_operation",String.class));assertEquals(1L,fixture.jdbc.queryForObject("SELECT revision FROM plan_review_operation",Long.class));
        assertEquals(DefaultHubConstants.JOB_STATUS_FAILED,fixture.jdbc.queryForObject("SELECT status FROM default_hub_sync_job",String.class));
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));
    }
    @Test void controlledTerminationStopsTheOriginalJobWithoutDeletingAnySuccessfulObjects(){
        fixture.tx.executeWithoutResult(status->operations.terminate("project",reservation.id(),0,"user"));
        assertEquals("TERMINATED",fixture.jdbc.queryForObject("SELECT state FROM plan_review_operation",String.class));
        assertEquals(DefaultHubConstants.JOB_STATUS_FAILED,fixture.jdbc.queryForObject("SELECT status FROM default_hub_sync_job",String.class));
        assertEquals(2,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));
        var request=JSON.parseObject(fixture.jdbc.queryForObject("SELECT request_json FROM plan_review_operation",String.class),DefaultHubPlanImportRequest.class);
        target.executeImportAsync("hub-job",request,new TestPlan(),"user",reservation);
        assertEquals(DefaultHubConstants.JOB_STATUS_FAILED,fixture.jdbc.queryForObject("SELECT status FROM default_hub_sync_job",String.class));
    }
}
