package io.metersphere.agent.controller;

import io.metersphere.agent.service.AgentSafeErrorMapper;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class AgentExecutionExceptionHandlerTests {
    private final AgentExecutionExceptionHandler handler=new AgentExecutionExceptionHandler(new AgentSafeErrorMapper());

    @Test void mapsNotFoundConflictRateLimitAndGatewayFailures(){
        MockHttpServletRequest request=new MockHttpServletRequest();request.addHeader("X-Trace-Id","trace-1");
        Assertions.assertEquals(HttpStatus.NOT_FOUND,handler.business(new MSException("MODEL_INVOCATION_NOT_FOUND"),request).getStatusCode());
        Assertions.assertEquals(HttpStatus.CONFLICT,handler.business(new MSException("CHECKPOINT_RESUME_CONFLICT"),request).getStatusCode());
        Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS,handler.business(new MSException("MODEL_BUDGET_EXCEEDED"),request).getStatusCode());
        Assertions.assertEquals(HttpStatus.BAD_GATEWAY,handler.business(new MSException("MAP_GATEWAY_SCHEMA_INVALID"),request).getStatusCode());
        Assertions.assertEquals("trace-1",handler.business(new MSException("CHECKPOINT_RESUME_CONFLICT"),request).getBody().getTraceId());
    }
}
