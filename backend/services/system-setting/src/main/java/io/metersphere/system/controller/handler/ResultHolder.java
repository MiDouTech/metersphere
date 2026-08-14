package io.metersphere.system.controller.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

@Data
public class ResultHolder {
    // 请求是否成功
    private int code = MsHttpResultCode.SUCCESS.getCode();
    // 描述信息，一般是错误信息
    private String message;
    // 详细描述信息, 如有异常，这里是详细日志
    private Object messageDetail;
    // Stable i18n key for the user-facing message.
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String messageKey;
    // Correlation id shared by the response header and server logs.
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String requestId;
    // Whether retrying the same operation is safe and meaningful.
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean retryable;
    // Structured validation errors. messageDetail remains during the compatibility window.
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> fieldErrors = Collections.emptyMap();
    // Whitelisted business context. Never store Throwable or stack trace values here.
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> context = Collections.emptyMap();
    // 返回数据
    private Object data = "";

    public ResultHolder() {
    }

    public ResultHolder(Object data) {
        this.data = data;
    }

    public ResultHolder(int code, String msg) {
        this.code = code;
        this.message = msg;
    }

    public ResultHolder(int code, String msg, Object data) {
        this.code = code;
        this.message = msg;
        this.data = data;
    }

    public ResultHolder(int code, String msg, Object messageDetail, Object data) {
        this.code = code;
        this.message = msg;
        this.messageDetail = messageDetail;
        this.data = data;
    }

    public static ResultHolder success(Object obj) {
        return new ResultHolder(obj);
    }

    public static ResultHolder error(int code, String message) {
        return new ResultHolder(code, message, null, null);
    }

    public static ResultHolder error(String message, String messageDetail) {
        return new ResultHolder(-1, message, messageDetail, null);
    }

    public static ResultHolder error(int code, String message, Object messageDetail) {
        return new ResultHolder(code, message, messageDetail, null);
    }

    public static ResultHolder structuredError(int code, String messageKey, String message, String requestId,
                                               boolean retryable, Map<String, String> fieldErrors) {
        ResultHolder result = new ResultHolder(code, message, fieldErrors, null);
        result.setMessageKey(messageKey);
        result.setRequestId(requestId);
        result.setRetryable(retryable);
        result.setFieldErrors(fieldErrors == null ? Collections.emptyMap() : fieldErrors);
        return result;
    }

    /**
     * 用于特殊情况，比如接口可正常返回，http状态码200，但是需要页面提示错误信息的情况
     * @param code 自定义 code
     * @param message 给前端返回的 message
     * @return
     */
    public static ResultHolder successCodeErrorInfo(int code, String message) {
        return new ResultHolder(code, message, null, null);
    }
}
