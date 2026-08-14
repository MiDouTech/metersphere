package io.metersphere.system.controller.handler;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import org.junit.jupiter.api.Test;
import org.apache.shiro.authz.UnauthorizedException;
import org.eclipse.jetty.io.EofException;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
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
import static org.mockito.Mockito.mockStatic;

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
    void shouldReturnMalformedJsonAsNonRetryableClientError() {
        RestControllerExceptionHandler handler = new RestControllerExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bug/add");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "JSON parse error: internal parser detail", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ResultHolder> result = handler.handleClientInputException(exception, request, response);

        assertEquals(400, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertEquals("api.validationFailed", result.getBody().getMessageKey());
        assertFalse(result.getBody().isRetryable());
        assertFalse(result.getBody().getMessage().contains("parser"));
    }

    @Test
    void shouldReturnSafeNonRetryableBusinessErrorForPlainMSException() {
        RestControllerExceptionHandler handler = new RestControllerExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bug/add");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<ResultHolder> result;
        try (var translator = mockStatic(Translator.class)) {
            translator.when(() -> Translator.get("未发布全局缺陷流程", "未发布全局缺陷流程"))
                    .thenReturn("未发布全局缺陷流程");
            result = handler.handlerMSException(new MSException("未发布全局缺陷流程"), request, response);
        }

        assertEquals(422, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertEquals("未发布全局缺陷流程", result.getBody().getMessage());
        assertEquals("api.businessError", result.getBody().getMessageKey());
        assertFalse(result.getBody().isRetryable());
        assertEquals(response.getHeader("X-Request-ID"), result.getBody().getRequestId());
    }

    @Test
    void shouldHideSensitivePlainMSExceptionDetails() {
        RestControllerExceptionHandler handler = new RestControllerExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bug/add");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<ResultHolder> result = handler.handlerMSException(
                new MSException("SQLState 42S22: unknown column expected_resolve_time"), request, response);

        assertEquals(500, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertEquals("Internal server error", result.getBody().getMessage());
        assertTrue(result.getBody().isRetryable());
        assertFalse(result.getBody().getMessage().contains("expected_resolve_time"));
    }

    @Test
    void shouldTreatExplicitBusinessResultCodeWithoutCustomMessageAsSafe() {
        RestControllerExceptionHandler handler = new RestControllerExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bug/add");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<ResultHolder> result;
        try (var translator = mockStatic(Translator.class)) {
            translator.when(() -> Translator.get("http_result_validate", "http_result_validate"))
                    .thenReturn("Validation failed");
            translator.when(() -> Translator.get("Validation failed", "Validation failed"))
                    .thenReturn("Validation failed");
            result = handler.handlerMSException(new MSException(MsHttpResultCode.UNPROCESSABLE_ENTITY), request, response);
        }

        assertEquals(422, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertFalse(result.getBody().isRetryable());
        assertEquals("api.businessError", result.getBody().getMessageKey());
    }

    @Test
    void shouldHideMSExceptionThatWrapsUnderlyingFailure() {
        RestControllerExceptionHandler handler = new RestControllerExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bug/add");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<ResultHolder> result = handler.handlerMSException(
                new MSException("保存缺陷失败", new IllegalStateException("database unavailable")), request, response);

        assertEquals(500, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertEquals("api.internalError", result.getBody().getMessageKey());
    }

    @Test
    void shouldHideCauseButKeepExplicitConfigurationErrorNonRetryable() {
        RestControllerExceptionHandler handler = new RestControllerExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/schedule/update");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<ResultHolder> result;
        try (var translator = mockStatic(Translator.class)) {
            translator.when(() -> Translator.get("http_result_validate", "http_result_validate"))
                    .thenReturn("Invalid configuration");
            translator.when(() -> Translator.get("Invalid configuration", "Invalid configuration"))
                    .thenReturn("Invalid configuration");
            result = handler.handlerMSException(new MSException(MsHttpResultCode.UNPROCESSABLE_ENTITY,
                    new IllegalArgumentException("secret cron parser state")), request, response);
        }

        assertEquals(422, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertFalse(result.getBody().isRetryable());
        assertFalse(result.getBody().getMessage().contains("secret"));
        assertNotNull(result.getBody().getRequestId());
    }

    @Test
    void shouldIgnoreStaticResourceEofWithoutReturningBusinessError() {
        RestControllerExceptionHandler handler = new RestControllerExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/app.js");

        ResponseEntity<Void> responseEntity = handler.handleEofException(request, new EofException());

        assertEquals(204, responseEntity.getStatusCode().value());
        assertEquals(null, responseEntity.getBody());
    }
}
