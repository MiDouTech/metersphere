package io.metersphere.agent.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentCaseImportDiagnosticsTests {
    private static final String SECRET="SECRET_IMPORT_DIAGNOSTIC_85319";
    private final AgentCaseImportService service=new AgentCaseImportService();
    private final ListAppender<ILoggingEvent> capture=new ListAppender<>();
    private Logger root;
    @BeforeEach void captureLogs(){
        root=(Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        capture.start();root.addAppender(capture);
    }
    @AfterEach void stop(){root.detachAppender(capture);capture.stop();}
    private RuntimeException failure(){return new IllegalStateException(SECRET,new java.sql.SQLException(SECRET,"42000",1064));}
    private void assertSafe(String phase,int count){
        assertEquals(count,capture.list.stream().filter(e->e.getFormattedMessage().contains("phase="+phase+" ")).count());
        assertTrue(capture.list.stream().noneMatch(e->e.getFormattedMessage().contains(SECRET)));
        assertTrue(capture.list.stream().noneMatch(e->e.getThrowableProxy()!=null));
    }
    @Test void precheckTechnicalCauseIsSafeAndNormalValidationIsUnchanged(){
        var jdbc=mock(JdbcTemplate.class,invocation->{if(invocation.getMethod().getName().equals("queryForObject"))throw new io.metersphere.sdk.exception.MSException(SECRET,new java.sql.SQLException(SECRET,"42000",1064));return RETURNS_DEFAULTS.answer(invocation);});ReflectionTestUtils.setField(service,"jdbc",jdbc);var request=new io.metersphere.functional.request.FunctionalCaseImportRequest();request.setProjectId("project");
        var failure=assertThrows(io.metersphere.sdk.exception.MSException.class,()->service.precheck(request,null,"request","user","WEB"));assertNull(failure.getCause());assertFalse(failure.getMessage().contains(SECRET));assertEquals(io.metersphere.system.controller.handler.result.MsHttpResultCode.FAILED,failure.getErrorCode());assertSafe("IMPORT_PRECHECK",1);
        var business=new io.metersphere.sdk.exception.MSException("safe validation");var ordinary=mock(JdbcTemplate.class,invocation->{if(invocation.getMethod().getName().equals("queryForObject"))throw business;return RETURNS_DEFAULTS.answer(invocation);});ReflectionTestUtils.setField(service,"jdbc",ordinary);assertSame(business,assertThrows(io.metersphere.sdk.exception.MSException.class,()->service.precheck(request,null,"request","user","WEB")));
    }
    @Test void technicalRuntimeIsSafeThroughRealMvcAndPermissionRefusalsStayUnchanged() throws Exception {
        var jdbc=mock(JdbcTemplate.class,invocation->{String method=invocation.getMethod().getName();if(method.equals("queryForList")){String sql=invocation.getArgument(0);if(sql.startsWith("SELECT * FROM functional_import_job"))return List.of(java.util.Map.of("project_id","project"));throw failure();}if(method.equals("queryForObject"))return 1L;return RETURNS_DEFAULTS.answer(invocation);});ReflectionTestUtils.setField(service,"jdbc",jdbc);var access=mock(AgentBusinessAccess.class);when(access.userHasPermission(anyString(),anyString(),anyString())).thenReturn(true);ReflectionTestUtils.setField(service,"access",access);
        var controller=new io.metersphere.agent.controller.AgentCaseImportController();ReflectionTestUtils.setField(controller,"service",service);var followups=mock(ImportFollowupCoordinator.class);ReflectionTestUtils.setField(controller,"followups",followups);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new io.metersphere.system.controller.handler.RestControllerExceptionHandler()).build();
        try(var session=mockStatic(io.metersphere.system.utils.SessionUtils.class);var translations=mockStatic(io.metersphere.sdk.util.Translator.class)) {
            session.when(io.metersphere.system.utils.SessionUtils::getUserId).thenReturn("user");translations.when(()->io.metersphere.sdk.util.Translator.get(anyString(),anyString())).thenAnswer(i->i.getArgument(0));
            var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/ai/case-imports/safe-job").accept(org.springframework.http.MediaType.APPLICATION_JSON)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isInternalServerError()).andReturn();var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));assertEquals(100500,body.path("code").asInt());assertFalse(body.toString().contains(SECRET));assertTrue(capture.list.stream().noneMatch(e->e.getThrowableProxy()!=null));assertSafe("IMPORT_STATUS",1);verify(access).userHasPermission(eq("user"),eq("project"),anyString());verifyNoInteractions(followups);
        }
        var request=new io.metersphere.functional.request.FunctionalCaseImportRequest();request.setProjectId("project");var denied=new org.apache.shiro.authz.UnauthorizedException("permission denied");var reject=mock(JdbcTemplate.class,invocation->{if(invocation.getMethod().getName().equals("queryForObject"))throw denied;return RETURNS_DEFAULTS.answer(invocation);});ReflectionTestUtils.setField(service,"jdbc",reject);assertSame(denied,assertThrows(org.apache.shiro.authz.UnauthorizedException.class,()->service.precheck(request,null,"request","user","WEB")));
        var governance=new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR","safe governance refusal");var governed=mock(JdbcTemplate.class,invocation->{if(invocation.getMethod().getName().equals("queryForObject"))throw governance;return RETURNS_DEFAULTS.answer(invocation);});ReflectionTestUtils.setField(service,"jdbc",governed);assertSame(governance,assertThrows(BusinessGovernanceException.class,()->service.precheck(request,null,"request","user","WEB")));
    }
    @Test void fileReadFailureHasSafeEventAndKeepsOriginalErrorCode() throws Exception {
        var jdbc=mock(JdbcTemplate.class,invocation->{if(invocation.getMethod().getName().equals("queryForObject"))return 1L;return RETURNS_DEFAULTS.answer(invocation);});ReflectionTestUtils.setField(service,"jdbc",jdbc);var access=mock(AgentBusinessAccess.class);when(access.userHasPermission(anyString(),anyString(),anyString())).thenReturn(true);ReflectionTestUtils.setField(service,"access",access);var request=new io.metersphere.functional.request.FunctionalCaseImportRequest();request.setProjectId("project");var file=mock(org.springframework.web.multipart.MultipartFile.class);when(file.getOriginalFilename()).thenReturn("cases.xlsx");when(file.getSize()).thenReturn(1L);when(file.getBytes()).thenThrow(new java.io.IOException(SECRET));var failure=assertThrows(io.metersphere.sdk.exception.MSException.class,()->service.precheck(request,file,"request","user","WEB"));assertEquals("IMPORT_FILE_READ_FAILED",failure.getMessage());assertNull(failure.getCause());assertSafe("IMPORT_FILE_READ",1);
    }
    @Test void scanFailureIsSafeAndNextScheduledCallRetries(){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),eq(String.class))).thenThrow(failure());
        ReflectionTestUtils.setField(service,"jdbc",jdbc);
        assertDoesNotThrow(service::drain);assertDoesNotThrow(service::drain);
        verify(jdbc,times(2)).queryForList(anyString(),eq(String.class));
        verifyNoMoreInteractions(jdbc);assertSafe("IMPORT_WORKER_SCAN",2);
    }
    @Test void recoveryFailureDoesNotEscapeOrPreventNextScheduledAttempt(){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),eq(String.class))).thenReturn(List.of("safe-job"));
        var transactions=mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenThrow(failure());
        ReflectionTestUtils.setField(service,"jdbc",jdbc);
        ReflectionTestUtils.setField(service,"transactionManager",transactions);
        assertDoesNotThrow(service::drain);assertDoesNotThrow(service::drain);
        verify(transactions,times(4)).getTransaction(any());
        verify(jdbc,times(2)).queryForList(anyString(),eq(String.class));
        verifyNoMoreInteractions(jdbc);
        assertSafe("IMPORT_ITEM_WRITE",2);assertSafe("IMPORT_ITEM_RECOVERY",2);
    }
    @Test void expiryFailureIsSafeAndRetriesWithoutClaimingSuccess(){
        var jdbc=mock(JdbcTemplate.class,invocation->{
            if(invocation.getMethod().getName().equals("update"))throw failure();
            return RETURNS_DEFAULTS.answer(invocation);
        });
        ReflectionTestUtils.setField(service,"jdbc",jdbc);
        assertDoesNotThrow(service::expire);assertDoesNotThrow(service::expire);
        assertEquals(2,mockingDetails(jdbc).getInvocations().size());
        assertTrue(mockingDetails(jdbc).getInvocations().stream().allMatch(i->i.getArgument(0).toString().contains("state='EXPIRED'")));
        assertSafe("IMPORT_EXPIRY",2);
    }
}
