package io.metersphere.system.controller.handler;

import org.junit.jupiter.api.Test;
import org.apache.shiro.authz.UnauthorizedException;
import org.eclipse.jetty.io.EofException;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RestControllerExceptionHandlerTests {

    @Test
    void shouldHideInternalExceptionDetailsAndReturnTraceableRequestId() {
        RestControllerExceptionHandler handler = new RestControllerExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/project/create");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RuntimeException exception = new RuntimeException(
                "java.sql.SQLException: SELECT user_name FROM user_mapper WHERE id = 1");

        ResponseEntity<ResultHolder> result = handler.handleException(exception, request, response);

        assertEquals(500, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertEquals("Internal server error", result.getBody().getMessage());
        assertEquals("api.internalError", result.getBody().getMessageKey());
        assertTrue(result.getBody().isRetryable());
        assertFalse(result.getBody().getMessage().contains("SQLException"));
        assertFalse(String.valueOf(result.getBody().getMessageDetail()).contains("SELECT"));
        assertNotNull(response.getHeader("X-Request-ID"));
        assertEquals(response.getHeader("X-Request-ID"), result.getBody().getRequestId());
    }

    @Test
    void shouldReuseIncomingRequestId() {
        RestControllerExceptionHandler handler = new RestControllerExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/project/detail");
        request.addHeader("X-Request-ID", "client-request-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<ResultHolder> result = handler.handleException(new RuntimeException("boom"), request, response);

        assertNotNull(result.getBody());
        assertEquals("client-request-id", result.getBody().getRequestId());
        assertEquals("client-request-id", response.getHeader("X-Request-ID"));
    }

    @Test
    void shouldSanitizeForbiddenResponse() {
        RestControllerExceptionHandler handler = new RestControllerExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/project/secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResultHolder result = handler.unauthorizedExceptionHandler(request, response,
                new UnauthorizedException("org.apache.shiro.authz.UnauthorizedException: internal-policy-name"));

        assertEquals(403, response.getStatus());
        assertEquals("auth.forbidden", result.getMessageKey());
        assertFalse(result.getMessage().contains("shiro"));
        assertFalse(result.getMessage().contains("internal-policy-name"));
        assertEquals(response.getHeader("X-Request-ID"), result.getRequestId());
    }

    @Test
    void shouldReturnFieldAndGlobalValidationErrorsWithoutTurningThemIntoInternalErrors() {
        RestControllerExceptionHandler handler = new RestControllerExceptionHandler();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "Email is required"));
        bindingResult.addError(new ObjectError("request", "At least one member is required"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                mock(MethodParameter.class), bindingResult);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/create");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResultHolder result = handler.handleValidationExceptions(exception, request, response);

        assertEquals("api.validationFailed", result.getMessageKey());
        assertEquals("Email is required", result.getFieldErrors().get("email"));
        assertEquals("At least one member is required", result.getFieldErrors().get("_global"));
        assertFalse(result.isRetryable());
        assertEquals(response.getHeader("X-Request-ID"), result.getRequestId());
    }

    @Test
    void shouldReturnStructuredErrorForStaticResourceEof() {
        RestControllerExceptionHandler handler = new RestControllerExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/app.js");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<Object> responseEntity = handler.handleEofException(request, response, new EofException());

        assertEquals(500, responseEntity.getStatusCode().value());
        assertTrue(responseEntity.getBody() instanceof ResultHolder);
        ResultHolder result = (ResultHolder) responseEntity.getBody();
        assertNotNull(result);
        assertEquals("api.internalError", result.getMessageKey());
        assertEquals(response.getHeader("X-Request-ID"), result.getRequestId());
    }
}
