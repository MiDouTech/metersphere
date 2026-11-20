package io.metersphere.agent.controller;

import io.metersphere.agent.service.AgentSafeErrorMapper;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class AgentExecutionExceptionHandlerTests {
    @Test void unknownFailureLogsOnlySafeDiagnosticsAndServerTrace(){
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(AgentExecutionExceptionHandler.class);
        var capture=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();capture.start();logger.addAppender(capture);
        try {
            var request=new MockHttpServletRequest();request.addHeader("X-Trace-Id","password=private-header");
            var failure=new org.springframework.dao.DataAccessResourceFailureException("private SQL payload",new java.sql.SQLException("token=private-value","08001",1045));
            var response=handler.unknown(failure,request);
            Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,response.getStatusCode());
            var entry=capture.list.getFirst();String log=entry.getFormattedMessage();
            Assertions.assertTrue(log.contains(response.getBody().getTraceId()));Assertions.assertTrue(log.contains("08001"));Assertions.assertTrue(log.contains("1045"));
            Assertions.assertFalse(log.contains("private"));Assertions.assertNull(entry.getThrowableProxy());
        }finally{logger.detachAppender(capture);capture.stop();}
    }
    @Test void disabledFeatureKeepsStableCodeAndSafeMessage() {
        var response = handler.business(new MSException(
                io.metersphere.system.controller.handler.result.CommonResultCode.FEATURE_DISABLED, "internal"),
                new MockHttpServletRequest());
        Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Assertions.assertEquals("FEATURE_DISABLED", response.getBody().getCode());
        Assertions.assertFalse(response.getBody().getMessage().contains("internal"));
    }
    private final AgentExecutionExceptionHandler handler=new AgentExecutionExceptionHandler(new AgentSafeErrorMapper());

    @Test void typedScopeDenialReturns403WithoutRawDetails(){
        var request=new MockHttpServletRequest();request.addHeader("X-Trace-Id","scope-trace");
        var response=handler.business(new MSException(io.metersphere.agent.constants.AgentErrorCode.SCOPE_DENIED,"internal scope details"),request);
        Assertions.assertEquals(HttpStatus.FORBIDDEN,response.getStatusCode());Assertions.assertEquals("SCOPE_DENIED",response.getBody().getCode());
        Assertions.assertNotEquals("scope-trace",response.getBody().getTraceId());Assertions.assertNotNull(response.getBody().getTraceId());Assertions.assertFalse(response.getBody().getMessage().contains("internal"));
    }
    @Test void mapsNotFoundConflictRateLimitAndGatewayFailures(){
        MockHttpServletRequest request=new MockHttpServletRequest();request.addHeader("X-Trace-Id","trace-1");
        Assertions.assertEquals(HttpStatus.NOT_FOUND,handler.business(new MSException("MODEL_INVOCATION_NOT_FOUND"),request).getStatusCode());
        Assertions.assertEquals(HttpStatus.CONFLICT,handler.business(new MSException("CHECKPOINT_RESUME_CONFLICT"),request).getStatusCode());
        Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS,handler.business(new MSException("MODEL_BUDGET_EXCEEDED"),request).getStatusCode());
        Assertions.assertEquals(HttpStatus.BAD_GATEWAY,handler.business(new MSException("MAP_GATEWAY_SCHEMA_INVALID"),request).getStatusCode());
        String trace=handler.business(new MSException("CHECKPOINT_RESUME_CONFLICT"),request).getBody().getTraceId();
        Assertions.assertNotEquals("trace-1",trace);Assertions.assertEquals(trace,handler.business(new MSException("CHECKPOINT_RESUME_CONFLICT"),request).getBody().getTraceId());
    }
}
