package io.metersphere.system.controller.handler;

import io.metersphere.sdk.exception.IResultCode;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import io.metersphere.system.utils.ServiceUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.lang.ShiroException;
import org.eclipse.jetty.io.EofException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;


@RestControllerAdvice
public class RestControllerExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestControllerExceptionHandler.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String INTERNAL_ERROR_MESSAGE = "Internal server error";
    private static final List<Pattern> SENSITIVE_ERROR_PATTERNS = List.of(
            Pattern.compile("(?:java|org|com)\\.[\\w.$]+(?:Exception|Error)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:select|insert|update|delete)\\s.+\\s(?:from|into|set)\\s", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("(?:mapper\\.xml|mybatis|jdbc|sqlstate|stack trace|caused by:)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("at\\s+[\\w.$]+\\([^)]*\\.java:\\d+\\)", Pattern.CASE_INSENSITIVE));

    /**
     * 处理数据校验异常
     * 返回具体字段的校验信息
     * http 状态码返回 400
     *
     * @param ex
     * @return
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultHolder handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request,
                                                   HttpServletResponse response) {
        Map<String, String> errors = new HashMap<>();
        int globalErrorIndex = 0;
        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            String errorMessage = StringUtils.defaultIfBlank(error.getDefaultMessage(), "Invalid value");
            if (error instanceof FieldError fieldError) {
                errors.put(fieldError.getField(), errorMessage);
            } else {
                String key = globalErrorIndex == 0 ? "_global" : "_global[" + globalErrorIndex + "]";
                errors.put(key, errorMessage);
                globalErrorIndex++;
            }
        }
        return ResultHolder.structuredError(MsHttpResultCode.VALIDATE_FAILED.getCode(), "api.validationFailed",
                "Validation failed", ensureRequestId(request, response), false, errors);
    }

    /**
     * http 状态码返回405
     *
     * @param exception 异常信息
     * @return
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResultHolder handleHttpRequestMethodNotSupportedException(HttpServletRequest request,
                                                                      HttpServletResponse response,
                                                                      Exception exception) {
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
        return ResultHolder.structuredError(HttpStatus.METHOD_NOT_ALLOWED.value(), "api.methodNotAllowed",
                "Request method not supported", ensureRequestId(request, response), false, Map.of());
    }

    /**
     * 根据 MSException 中的 errorCode
     * 设置对应的 Http 状态码，以及业务状态码和错误提示
     *
     * @param e
     * @return
     */
    @ExceptionHandler(MSException.class)
    public ResponseEntity<ResultHolder> handlerMSException(MSException e, HttpServletRequest request,
                                                            HttpServletResponse response) {
        IResultCode errorCode = e.getErrorCode();
        if (!isSafeBusinessException(e)) {
            String requestId = recordInternalError(request, response, e);
            if (errorCode != null && (!(errorCode instanceof MsHttpResultCode) || errorCode.getCode() % 1000 < 500)) {
                int httpStatus = errorCode instanceof MsHttpResultCode ? errorCode.getCode() % 1000
                        : HttpStatus.UNPROCESSABLE_ENTITY.value();
                String safeMessage = Translator.get(errorCode.getMessage(), errorCode.getMessage());
                return ResponseEntity.status(httpStatus)
                        .body(ResultHolder.structuredError(errorCode.getCode(), messageKey(errorCode), safeMessage,
                                requestId, false, Map.of()));
            }
            return ResponseEntity.internalServerError()
                    .body(ResultHolder.structuredError(MsHttpResultCode.FAILED.getCode(), "api.internalError",
                            INTERNAL_ERROR_MESSAGE, requestId, true, Map.of()));
        }
        if (errorCode == null) {
            String message = Translator.get(e.getMessage(), e.getMessage());
            return ResponseEntity.unprocessableEntity()
                    .body(ResultHolder.structuredError(MsHttpResultCode.UNPROCESSABLE_ENTITY.getCode(), "api.businessError",
                            message, ensureRequestId(request, response), false, Map.of()));
        }

        int code = errorCode.getCode();
        String message = StringUtils.defaultIfBlank(e.getMessage(), errorCode.getMessage());
        message = Translator.get(message, message);

        if (errorCode instanceof MsHttpResultCode) {
            // 如果是 MsHttpResultCode，则设置响应的状态码，取状态码的后三位
            if (errorCode.equals(MsHttpResultCode.NOT_FOUND)) {
                message = getNotFoundMessage(message);
            }
            return ResponseEntity.status(code % 1000)
                    .body(ResultHolder.structuredError(code, messageKey(errorCode), message,
                            ensureRequestId(request, response), code % 1000 >= 500, Map.of()));
        } else {
            return ResponseEntity.unprocessableEntity()
                    .body(ResultHolder.structuredError(code, messageKey(errorCode), message,
                            ensureRequestId(request, response), false, Map.of()));
        }
    }

    /**
     * 当抛出 NOT_FOUND，拼接资源名称
     *
     * @param message
     * @return
     */
    private static String getNotFoundMessage(String message) {
        String resourceName = ServiceUtils.getResourceName();
        if (StringUtils.isNotBlank(resourceName)) {
            message = String.format(message, Translator.get(resourceName, resourceName));
        } else {
            message = String.format(message, Translator.get("resource.name"));
        }
        ServiceUtils.clearResourceName();
        return message;
    }

    @ExceptionHandler({Exception.class})
    public ResponseEntity<ResultHolder> handleException(Exception e, HttpServletRequest request,
                                                         HttpServletResponse response) {
        String requestId = recordInternalError(request, response, e);
        return ResponseEntity.internalServerError()
                .body(ResultHolder.structuredError(MsHttpResultCode.FAILED.getCode(), "api.internalError",
                        INTERNAL_ERROR_MESSAGE, requestId, true, Map.of()));
    }

    @ExceptionHandler({EofException.class})
    public ResponseEntity<Void> handleEofException(HttpServletRequest request, Exception e) {
        LOGGER.debug("Client disconnected before the response completed, method={}, uri={}",
                request == null ? "" : request.getMethod(), request == null ? "" : request.getRequestURI());
        return ResponseEntity.noContent().build();
    }

    /*=========== Shiro 异常拦截==============*/
    @ExceptionHandler(ShiroException.class)
    public ResultHolder exceptionHandler(HttpServletRequest request, HttpServletResponse response, Exception exception) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return ResultHolder.structuredError(MsHttpResultCode.UNAUTHORIZED.getCode(), "auth.sessionInvalid",
                "Authentication required", ensureRequestId(request, response), false, Map.of());
    }

    /*=========== Shiro 异常拦截==============*/
    @ExceptionHandler(UnauthorizedException.class)
    public ResultHolder unauthorizedExceptionHandler(HttpServletRequest request, HttpServletResponse response, Exception exception) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        return ResultHolder.structuredError(MsHttpResultCode.FORBIDDEN.getCode(), "auth.forbidden",
                "Access forbidden", ensureRequestId(request, response), false, Map.of());
    }

    /**
     * 格式化异常信息
     * 当出现未知异常时，将错误栈信息格式化返回
     *
     * @param e
     * @return
     */
    private String recordInternalError(HttpServletRequest request, HttpServletResponse response, Exception exception) {
        String requestId = ensureRequestId(request, response);
        LOGGER.error("Unhandled request error, requestId={}, method={}, uri={}, user={}, organization={}, project={}",
                requestId, request == null ? "" : request.getMethod(), request == null ? "" : request.getRequestURI(),
                request == null || request.getUserPrincipal() == null ? "" : request.getUserPrincipal().getName(),
                request == null ? "" : request.getHeader("ORGANIZATION"),
                request == null ? "" : request.getHeader("PROJECT"), exception);
        return requestId;
    }

    private String ensureRequestId(HttpServletRequest request, HttpServletResponse response) {
        String existing = request == null ? null : request.getHeader(REQUEST_ID_HEADER);
        String requestId = StringUtils.defaultIfBlank(existing, UUID.randomUUID().toString());
        if (response != null) {
            response.setHeader(REQUEST_ID_HEADER, requestId);
        }
        return requestId;
    }

    private String messageKey(IResultCode errorCode) {
        if (errorCode == MsHttpResultCode.NOT_FOUND) return "api.notFound";
        if (errorCode == MsHttpResultCode.CONFLICT) return "api.conflict";
        if (errorCode == MsHttpResultCode.UNAUTHORIZED) return "api.unauthorized";
        if (errorCode == MsHttpResultCode.FORBIDDEN) return "api.forbidden";
        return "api.businessError";
    }

    @ExceptionHandler({BindException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    public ResponseEntity<ResultHolder> handleClientInputException(Exception exception, HttpServletRequest request,
                                                                    HttpServletResponse response) {
        return ResponseEntity.badRequest().body(ResultHolder.structuredError(
                MsHttpResultCode.VALIDATE_FAILED.getCode(), "api.validationFailed", "Invalid request parameters",
                ensureRequestId(request, response), false, Map.of()));
    }

    private boolean isSafeBusinessException(MSException exception) {
        if (exception.getCause() != null) {
            return false;
        }
        String effectiveMessage = exception.getMessage();
        if (StringUtils.isBlank(effectiveMessage)) return exception.getErrorCode() != null;
        return SENSITIVE_ERROR_PATTERNS.stream()
                .noneMatch(pattern -> pattern.matcher(effectiveMessage).find());
    }
}
