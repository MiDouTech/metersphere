package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentApiErrorDTO;
import io.metersphere.agent.service.AgentSafeErrorMapper;
import io.metersphere.sdk.exception.MSException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "io.metersphere.agent.controller")
public class AgentExecutionExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentExecutionExceptionHandler.class);
    private final AgentSafeErrorMapper mapper;

    public AgentExecutionExceptionHandler(AgentSafeErrorMapper mapper) {
        this.mapper = mapper;
    }

    @ExceptionHandler(MSException.class)
    public ResponseEntity<AgentApiErrorDTO> business(MSException error, HttpServletRequest request) {
        String traceId = traceId(request);
        LOGGER.warn("AI execution request rejected, traceId={}, code={}", traceId, safeCode(error));
        return ResponseEntity.badRequest().body(mapper.toApiError(error, traceId));
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<AgentApiErrorDTO> forbidden(AuthorizationException error, HttpServletRequest request) {
        String traceId = traceId(request);
        LOGGER.warn("AI execution permission denied, traceId={}", traceId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapper.toApiError(new MSException("PERMISSION_DENIED"), traceId));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            MissingServletRequestParameterException.class, IllegalArgumentException.class})
    public ResponseEntity<AgentApiErrorDTO> validation(Exception error, HttpServletRequest request) {
        String traceId = traceId(request);
        LOGGER.warn("AI execution validation failed, traceId={}, type={}", traceId, error.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(mapper.toApiError(new MSException("VALIDATION_ERROR"), traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AgentApiErrorDTO> unknown(Exception error, HttpServletRequest request) {
        String traceId = traceId(request);
        LOGGER.error("AI execution internal error, traceId={}", traceId, error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapper.toApiError(error, traceId));
    }

    private String traceId(HttpServletRequest request) {
        return StringUtils.defaultIfBlank(request.getHeader("X-Trace-Id"), UUID.randomUUID().toString());
    }

    private String safeCode(MSException error) {
        String value = error.getMessage();
        return StringUtils.isNotBlank(value) && value.matches("^[A-Z][A-Z0-9_]{2,63}$") ? value : "BUSINESS_ERROR";
    }
}
