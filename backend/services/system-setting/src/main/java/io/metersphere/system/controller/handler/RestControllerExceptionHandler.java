package io.metersphere.system.controller.handler;

import io.metersphere.sdk.exception.IResultCode;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import io.metersphere.system.utils.ServiceUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.lang.ShiroException;
import org.eclipse.jetty.io.EofException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@RestControllerAdvice
public class RestControllerExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestControllerExceptionHandler.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String INTERNAL_ERROR_MESSAGE = "Internal server error";

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
        if (errorCode == null) {
            // 如果抛出异常没有设置状态码，则返回错误 message
            String requestId = recordInternalError(request, response, e);
            return ResponseEntity.internalServerError()
                    .body(ResultHolder.structuredError(MsHttpResultCode.FAILED.getCode(), "api.internalError",
                            INTERNAL_ERROR_MESSAGE, requestId, true, Map.of()));
        }

        int code = errorCode.getCode();
        String message = errorCode.getMessage();
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
            // 响应码返回 500，设置业务状态码
            recordInternalError(request, response, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResultHolder.structuredError(code, messageKey(errorCode), Translator.get(message, message),
                            ensureRequestId(request, response), true, Map.of()));
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
    public ResponseEntity<Object> handleEofException(HttpServletRequest request, HttpServletResponse response, Exception e) {
        String requestURI = request.getRequestURI();
        String requestId = recordInternalError(request, response, e);
        return ResponseEntity.internalServerError()
                .body(ResultHolder.structuredError(MsHttpResultCode.FAILED.getCode(), "api.internalError",
                        INTERNAL_ERROR_MESSAGE, requestId, true, Map.of()));
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
}
