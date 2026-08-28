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
        String code=safeCode(error);
        HttpStatus status=status(code);
        LOGGER.warn("AI execution request rejected, traceId={}, code={}, httpStatus={}", traceId, code,status.value());
        return ResponseEntity.status(status).body(mapper.toApiError(new MSException(code), traceId));
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

    private HttpStatus status(String code){
        if("AUTHENTICATION_REQUIRED".equals(code)||code.endsWith("_TOKEN_REQUIRED"))return HttpStatus.UNAUTHORIZED;
        if(code.contains("FORBIDDEN")||code.contains("PERMISSION_DENIED")||code.endsWith("_NOT_ACCESSIBLE"))return HttpStatus.FORBIDDEN;
        if(code.contains("NOT_FOUND")||code.endsWith("_NOT_EXIST"))return HttpStatus.NOT_FOUND;
        if(code.contains("EXPIRED"))return HttpStatus.GONE;
        if(code.contains("CONFLICT")||code.startsWith("ALREADY_")||code.endsWith("_ALREADY_ACTIVE")||code.endsWith("_ALREADY_LEASED"))return HttpStatus.CONFLICT;
        if(code.contains("RATE_LIMIT")||code.contains("QUOTA")||code.contains("BUDGET")||code.contains("INVOCATION_LIMIT"))return HttpStatus.TOO_MANY_REQUESTS;
        if(code.startsWith("MAP_GATEWAY_")||code.startsWith("UPSTREAM_"))return HttpStatus.BAD_GATEWAY;
        if(code.contains("NOT_CONFIGURED")||code.contains("NOT_AVAILABLE"))return HttpStatus.SERVICE_UNAVAILABLE;
        return HttpStatus.BAD_REQUEST;
    }
}
